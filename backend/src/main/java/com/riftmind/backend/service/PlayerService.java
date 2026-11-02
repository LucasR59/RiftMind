package com.riftmind.backend.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.riftmind.backend.exception.InvalidRequestException;
import com.riftmind.backend.exception.PlayerNotFoundException;
import com.riftmind.backend.exception.RefreshCooldownException;
import com.riftmind.backend.model.Player;
import com.riftmind.backend.repository.PlayerRepository;
import com.riftmind.backend.riot.Platform;
import com.riftmind.backend.riot.RiotClient;
import com.riftmind.backend.riot.RiotNotFoundException;
import com.riftmind.backend.riot.RiotProperties;
import com.riftmind.backend.riot.dto.AccountDto;
import com.riftmind.backend.riot.dto.LeagueEntryDto;
import com.riftmind.backend.riot.dto.SummonerDto;

/**
 * Recherche d'un joueur par Riot ID (tâche 2.4) et mise à jour depuis Riot (tâche 5.5).
 *
 * <p>Pas de transaction autour de la méthode : les appels à Riot peuvent durer plusieurs secondes (attente du
 * limiteur de requêtes), et une transaction garderait pendant tout ce temps une connexion à la base. Chaque accès au
 * repository a donc sa propre transaction courte.
 */
@Service
public class PlayerService {

    private static final Logger log = LoggerFactory.getLogger(PlayerService.class);

    static final String ACCOUNT_BY_RIOT_ID = "/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}";
    static final String ACCOUNT_BY_PUUID = "/riot/account/v1/accounts/by-puuid/{puuid}";
    static final String SUMMONER_BY_PUUID = "/lol/summoner/v4/summoners/by-puuid/{puuid}";

    private final RiotClient riotClient;
    private final PlayerRepository playerRepository;
    private final LeagueService leagueService;
    private final MatchIngestionService matchIngestionService;
    private final Duration refreshCooldown;
    private final Clock clock;

    public PlayerService(RiotClient riotClient, PlayerRepository playerRepository, LeagueService leagueService,
            MatchIngestionService matchIngestionService, RiotProperties riotProperties, Clock clock) {
        this.riotClient = riotClient;
        this.playerRepository = playerRepository;
        this.leagueService = leagueService;
        this.matchIngestionService = matchIngestionService;
        this.refreshCooldown = riotProperties.refreshCooldown();
        this.clock = clock;
    }

    /**
     * Renvoie le joueur depuis la base s'il y est. Sinon, l'appelle chez Riot (Account-V1, Summoner-V4, puis
     * League-V4 pour ses rangs) et l'enregistre avec ses rangs.
     *
     * <ul>
     *   <li>La recherche en base ignore la casse. Le joueur enregistré garde l'écriture exacte renvoyée par Riot
     *       (ex. {@code joueur test#euw} → {@code Joueur Test#EUW}).</li>
     *   <li>Si le PUUID est déjà en base sous un autre Riot ID ou une autre plateforme (le joueur a changé de nom ou de
     *       serveur), la ligne existante est mise à jour au lieu d'en créer une deuxième.</li>
     *   <li>Les données en base ne sont pas rafraîchies ici, même anciennes : c'est le rôle du bouton « Mettre à
     *       jour » (tâche 5.5). Un Riot ID repris par un autre joueur renvoie donc l'ancien propriétaire jusqu'à ce
     *       rafraîchissement.</li>
     * </ul>
     *
     * @throws PlayerNotFoundException                    si le Riot ID n'existe pas, ou s'il n'a pas de profil League
     *                                                    of Legends sur cette plateforme
     * @throws InvalidRequestException                    si le nom ou le tag est vide
     * @throws com.riftmind.backend.riot.RiotApiException si Riot est indisponible ou refuse l'appel
     */
    public Player getOrFetch(Platform platform, String gameName, String tagLine) {
        String name = requireNotBlank(gameName, "nom");
        String tag = requireNotBlank(tagLine, "tag");

        Optional<Player> known = playerRepository.findByRiotId(platform, name, tag);
        if (known.isPresent()) {
            return known.get();
        }

        // Tous les appels à Riot d'abord : si l'un échoue, rien n'est enregistré et la recherche suivante réessaie
        AccountDto account = fetchAccount(platform, name, tag);
        SummonerDto summoner = fetchSummoner(platform, account, name, tag);
        List<LeagueEntryDto> ranks = leagueService.fetch(platform, account.puuid());

        Instant now = now();
        Player player = save(platform, account, summoner, name, tag, now);
        leagueService.save(player.getPuuid(), ranks, now);
        return player;
    }

    /**
     * Met à jour le joueur depuis Riot : Riot ID (il a pu changer), niveau, icône, rangs, puis ses
     * {@value MatchIngestionService#DEFAULT_COUNT} dernières parties (seules les nouvelles sont demandées).
     *
     * <p>Un délai minimum ({@code riot.refresh-cooldown}, 2 min par défaut) sépare deux mises à jour du même joueur,
     * pour ménager les limites de l'API Riot. Il est réservé en base avant tout appel à Riot
     * ({@link PlayerRepository#claimRefresh}) : deux clics simultanés ne lancent qu'une mise à jour. Si Riot échoue, la
     * réservation est annulée et le joueur peut réessayer tout de suite ; ce qui a déjà été enregistré (joueur, rangs,
     * parties récupérées) est gardé.
     *
     * @throws PlayerNotFoundException                    si aucun joueur n'a ce PUUID
     * @throws RefreshCooldownException                   si la dernière mise à jour est trop récente
     * @throws com.riftmind.backend.riot.RiotApiException si Riot est indisponible ou refuse un appel
     */
    public Player refresh(String puuid) {
        Player player = playerRepository.findById(puuid)
                .orElseThrow(() -> PlayerNotFoundException.unknownPuuid(puuid));
        Instant previous = player.getLastRefreshedAt();
        Instant now = now();
        if (playerRepository.claimRefresh(puuid, now, now.minus(refreshCooldown)) == 0) {
            // Relu en base : une autre requête vient peut-être de faire la mise à jour
            Instant last = playerRepository.findById(puuid).map(Player::getLastRefreshedAt).orElse(previous);
            throw new RefreshCooldownException(Duration.between(now, last.plus(refreshCooldown)));
        }
        try {
            Platform platform = player.getPlatform();
            AccountDto account = riotClient.get(platform.accountRoute(), ACCOUNT_BY_PUUID, AccountDto.class, puuid);
            SummonerDto summoner = riotClient.get(platform, SUMMONER_BY_PUUID, SummonerDto.class, puuid);
            List<LeagueEntryDto> ranks = leagueService.fetch(platform, puuid);

            player.refresh(displayName(account.gameName(), player.getGameName()),
                    displayName(account.tagLine(), player.getTagLine()), platform,
                    Math.toIntExact(summoner.summonerLevel()), summoner.profileIconId(), now);
            Player saved = playerRepository.save(player);
            leagueService.save(puuid, ranks, now);
            matchIngestionService.syncRecent(saved);
            log.info("Joueur {} mis à jour", saved);
            return saved;
        } catch (RuntimeException e) {
            playerRepository.releaseRefresh(puuid, now, previous);
            throw e;
        }
    }

    private AccountDto fetchAccount(Platform platform, String name, String tag) {
        try {
            return riotClient.get(platform.accountRoute(), ACCOUNT_BY_RIOT_ID, AccountDto.class, name, tag);
        } catch (RiotNotFoundException e) {
            throw PlayerNotFoundException.unknownRiotId(name, tag, platform);
        }
    }

    private SummonerDto fetchSummoner(Platform platform, AccountDto account, String name, String tag) {
        try {
            return riotClient.get(platform, SUMMONER_BY_PUUID, SummonerDto.class, account.puuid());
        } catch (RiotNotFoundException e) {
            // Le compte Riot existe (Account-V1 est mondial) mais n'a jamais joué à LoL sur ce serveur
            throw PlayerNotFoundException.noProfileOnPlatform(displayName(account.gameName(), name),
                    displayName(account.tagLine(), tag), platform);
        }
    }

    private Player save(Platform platform, AccountDto account, SummonerDto summoner, String name, String tag,
            Instant now) {
        // Riot renvoie l'écriture exacte du Riot ID. Repli sur la saisie si le compte n'en a pas.
        String gameName = displayName(account.gameName(), name);
        String tagLine = displayName(account.tagLine(), tag);
        int level = Math.toIntExact(summoner.summonerLevel());

        Player player = playerRepository.findById(account.puuid())
                .map(existing -> {
                    log.info("Joueur {} déjà connu sous {} sur {} : mise à jour", account.puuid(),
                            existing.getRiotId(), existing.getPlatform());
                    existing.refresh(gameName, tagLine, platform, level, summoner.profileIconId(), now);
                    return existing;
                })
                .orElseGet(() -> new Player(account.puuid(), gameName, tagLine, platform, level,
                        summoner.profileIconId(), now));
        try {
            return playerRepository.save(player);
        } catch (DataIntegrityViolationException e) {
            // Deux recherches simultanées du même nouveau joueur : l'autre l'a enregistré en premier
            log.debug("Joueur {} enregistré en parallèle par une autre requête", account.puuid());
            return playerRepository.findById(account.puuid()).orElseThrow(() -> e);
        }
    }

    /** PostgreSQL stocke les dates à la microseconde : on tronque pour renvoyer la même valeur qu'à la relecture. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static String requireNotBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException("Le " + field + " du Riot ID ne doit pas être vide.");
        }
        return value.strip();
    }

    private static String displayName(String fromRiot, String fallback) {
        return fromRiot != null && !fromRiot.isBlank() ? fromRiot : fallback;
    }
}
