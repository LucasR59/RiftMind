package com.riftmind.backend.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.riftmind.backend.exception.PlayerNotFoundException;
import com.riftmind.backend.model.Player;
import com.riftmind.backend.repository.PlayerRepository;
import com.riftmind.backend.riot.Platform;
import com.riftmind.backend.riot.RiotClient;
import com.riftmind.backend.riot.RiotNotFoundException;
import com.riftmind.backend.riot.dto.AccountDto;
import com.riftmind.backend.riot.dto.SummonerDto;

/**
 * Recherche d'un joueur par Riot ID (tâche 2.4).
 *
 * <p>Pas de transaction autour de la méthode : les appels à Riot peuvent durer plusieurs secondes (attente du
 * limiteur de requêtes), et une transaction garderait pendant tout ce temps une connexion à la base. Chaque accès au
 * repository a donc sa propre transaction courte.
 */
@Service
public class PlayerService {

    private static final Logger log = LoggerFactory.getLogger(PlayerService.class);

    static final String ACCOUNT_BY_RIOT_ID = "/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}";
    static final String SUMMONER_BY_PUUID = "/lol/summoner/v4/summoners/by-puuid/{puuid}";

    private final RiotClient riotClient;
    private final PlayerRepository playerRepository;
    private final Clock clock;

    public PlayerService(RiotClient riotClient, PlayerRepository playerRepository, Clock clock) {
        this.riotClient = riotClient;
        this.playerRepository = playerRepository;
        this.clock = clock;
    }

    /**
     * Renvoie le joueur depuis la base s'il y est. Sinon, l'appelle chez Riot (Account-V1 puis Summoner-V4) et
     * l'enregistre.
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
     * @throws IllegalArgumentException                   si le nom ou le tag est vide
     * @throws com.riftmind.backend.riot.RiotApiException si Riot est indisponible ou refuse l'appel
     */
    public Player getOrFetch(Platform platform, String gameName, String tagLine) {
        String name = requireNotBlank(gameName, "gameName");
        String tag = requireNotBlank(tagLine, "tagLine");

        Optional<Player> known = playerRepository.findByRiotId(platform, name, tag);
        if (known.isPresent()) {
            return known.get();
        }

        AccountDto account = fetchAccount(platform, name, tag);
        SummonerDto summoner = fetchSummoner(platform, account, name, tag);
        return save(platform, account, summoner, name, tag);
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

    private Player save(Platform platform, AccountDto account, SummonerDto summoner, String name, String tag) {
        Instant now = clock.instant();
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

    private static String requireNotBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " ne doit pas être vide");
        }
        return value.strip();
    }

    private static String displayName(String fromRiot, String fallback) {
        return fromRiot != null && !fromRiot.isBlank() ? fromRiot : fallback;
    }
}
