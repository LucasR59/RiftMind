package com.riftmind.backend.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.riftmind.backend.model.Match;
import com.riftmind.backend.model.MatchParticipant;
import com.riftmind.backend.model.Player;
import com.riftmind.backend.repository.MatchParticipantRepository;
import com.riftmind.backend.repository.MatchRepository;
import com.riftmind.backend.riot.Platform;
import com.riftmind.backend.riot.RegionalRoute;
import com.riftmind.backend.riot.RiotApiException;
import com.riftmind.backend.riot.RiotClient;
import com.riftmind.backend.riot.RiotNotFoundException;
import com.riftmind.backend.riot.dto.InfoDto;
import com.riftmind.backend.riot.dto.MatchDto;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Récupération des dernières parties d'un joueur et enregistrement en base (tâche 4.4).
 *
 * <p>Une partie terminée ne change jamais : elle n'est demandée à Riot qu'une seule fois. Une synchronisation coûte
 * donc 1 appel (la liste des derniers identifiants), plus 1 appel par partie pas encore en base.
 *
 * <p>Comme {@link PlayerService}, aucune transaction n'est ouverte pendant les appels à Riot : toutes les parties sont
 * d'abord récupérées, puis enregistrées ensemble dans une seule transaction.
 */
@Service
public class MatchIngestionService {

    private static final Logger log = LoggerFactory.getLogger(MatchIngestionService.class);

    static final String MATCH_IDS_BY_PUUID = "/lol/match/v5/matches/by-puuid/{puuid}/ids?start=0&count={count}";
    static final String MATCH_BY_ID = "/lol/match/v5/matches/{matchId}";
    /** Nombre de parties récupérées par défaut. */
    public static final int DEFAULT_COUNT = 20;
    /** Maximum accepté par Match-V5 pour {@code count}. */
    static final int MAX_COUNT = 100;
    private static final ParameterizedTypeReference<List<String>> ID_LIST = new ParameterizedTypeReference<>() {
    };

    private final RiotClient riotClient;
    private final MatchRepository matchRepository;
    private final MatchParticipantRepository participantRepository;
    private final TransactionTemplate transaction;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public MatchIngestionService(RiotClient riotClient, MatchRepository matchRepository,
            MatchParticipantRepository participantRepository, PlatformTransactionManager transactionManager,
            JsonMapper jsonMapper, Clock clock) {
        this.riotClient = riotClient;
        this.matchRepository = matchRepository;
        this.participantRepository = participantRepository;
        this.transaction = new TransactionTemplate(transactionManager);
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    /**
     * Bilan d'une synchronisation.
     *
     * @param listed       identifiants renvoyés par Riot
     * @param alreadyKnown parties déjà en base, non redemandées à Riot
     * @param saved        parties récupérées et enregistrées par cet appel
     */
    public record SyncResult(int listed, int alreadyKnown, int saved) {
    }

    /** Une partie récupérée chez Riot, prête à être enregistrée. */
    private record FetchedMatch(Match match, List<MatchParticipant> participants) {
    }

    /** Les {@value #DEFAULT_COUNT} dernières parties du joueur, sur la région de sa plateforme. */
    public SyncResult syncRecent(Player player) {
        return syncRecent(player.getPlatform(), player.getPuuid(), DEFAULT_COUNT);
    }

    /**
     * Récupère les {@code count} dernières parties du joueur, toutes files confondues :
     * <ol>
     *   <li>liste des derniers identifiants (Match-V5, région de routage de la plateforme) ;</li>
     *   <li>les parties déjà en base sont écartées ;</li>
     *   <li>les parties manquantes sont demandées une par une ;</li>
     *   <li>tout est enregistré dans une seule transaction.</li>
     * </ol>
     *
     * <p>Une partie introuvable chez Riot (404) ou illisible est ignorée. Si Riot échoue en cours de route (limite de
     * requêtes, panne), les parties déjà récupérées sont quand même enregistrées, puis l'erreur est relancée : la
     * synchronisation suivante reprendra là où celle-ci s'est arrêtée.
     *
     * @param count nombre de parties, de 1 à {@value #MAX_COUNT}
     * @throws RiotApiException si Riot est indisponible ou refuse un appel
     */
    public SyncResult syncRecent(Platform platform, String puuid, int count) {
        if (count < 1 || count > MAX_COUNT) {
            throw new IllegalArgumentException("count doit être entre 1 et " + MAX_COUNT + " : " + count);
        }
        RegionalRoute region = platform.regionalRoute();
        List<String> ids = riotClient.get(region, MATCH_IDS_BY_PUUID, ID_LIST, puuid, count);
        if (ids == null || ids.isEmpty()) {
            return new SyncResult(0, 0, 0);
        }

        Set<String> known = matchRepository.findExistingIds(ids);
        List<String> missing = ids.stream().filter(id -> !known.contains(id)).distinct().toList();

        List<FetchedMatch> fetched = new ArrayList<>();
        RiotApiException failure = null;
        for (String matchId : missing) {
            try {
                FetchedMatch match = fetch(region, matchId);
                if (match != null) {
                    fetched.add(match);
                }
            } catch (RiotNotFoundException e) {
                log.warn("Match {} listé pour le joueur {} mais introuvable chez Riot : ignoré", matchId, puuid);
            } catch (RiotApiException e) {
                failure = e;
                break;
            }
        }

        int saved = save(fetched);
        log.info("Matchs du joueur {} : {} listés, {} déjà connus, {} enregistrés", puuid, ids.size(), known.size(),
                saved);
        if (failure != null) {
            log.warn("Synchronisation du joueur {} interrompue après {} matchs sur {} : {}", puuid, fetched.size(),
                    missing.size(), failure.getMessage());
            throw failure;
        }
        return new SyncResult(ids.size(), known.size(), saved);
    }

    /** Demande une partie à Riot et la convertit, ou {@code null} si la réponse est illisible. */
    private FetchedMatch fetch(RegionalRoute region, String matchId) {
        String raw = riotClient.get(region, MATCH_BY_ID, String.class, matchId);
        MatchDto dto;
        try {
            dto = jsonMapper.readValue(raw, MatchDto.class);
        } catch (JacksonException e) {
            log.error("Match {} illisible : ignoré", matchId, e);
            return null;
        }
        if (dto == null || dto.info() == null || dto.info().participants() == null) {
            log.error("Match {} sans informations de partie : ignoré", matchId);
            return null;
        }
        InfoDto info = dto.info();
        Match match = new Match(matchId, platformOf(matchId, info), info.queueId(),
                Objects.requireNonNullElse(info.gameVersion(), ""),
                info.gameStart(), Math.toIntExact(info.durationSeconds()), false, raw, now());
        List<MatchParticipant> participants = info.participants().stream()
                .map(p -> MatchParticipant.of(match, p))
                .toList();
        return new FetchedMatch(match, participants);
    }

    /**
     * Enregistre les parties dans une seule transaction. Si une autre requête a enregistré l'une d'elles entre-temps
     * (deux synchronisations simultanées du même joueur, ou de deux joueurs de la même partie), l'écriture est
     * refaite une fois sans les parties devenues connues.
     *
     * @return nombre de parties enregistrées par cet appel
     */
    private int save(List<FetchedMatch> fetched) {
        if (fetched.isEmpty()) {
            return 0;
        }
        try {
            return saveInTransaction(fetched);
        } catch (DataIntegrityViolationException e) {
            if (!DuplicateKeys.isDuplicateKey(e)) {
                throw e;
            }
            Set<String> savedMeanwhile = matchRepository.findExistingIds(
                    fetched.stream().map(f -> f.match().getMatchId()).toList());
            log.debug("Matchs {} enregistrés en parallèle par une autre requête", savedMeanwhile);
            return saveInTransaction(fetched.stream()
                    .filter(f -> !savedMeanwhile.contains(f.match().getMatchId()))
                    .toList());
        }
    }

    private int saveInTransaction(List<FetchedMatch> fetched) {
        if (fetched.isEmpty()) {
            return 0;
        }
        transaction.executeWithoutResult(status -> {
            matchRepository.saveAll(fetched.stream().map(FetchedMatch::match).toList());
            participantRepository.saveAll(fetched.stream().flatMap(f -> f.participants().stream()).toList());
            // Écrit tout de suite pour que les erreurs (clé dupliquée…) sortent ici, déjà traduites par Spring
            participantRepository.flush();
        });
        return fetched.size();
    }

    /** {@code info.platformId}, ou à défaut le préfixe de l'identifiant ({@code EUW1} pour {@code EUW1_123}). */
    private static String platformOf(String matchId, InfoDto info) {
        if (info.platformId() != null && !info.platformId().isBlank()) {
            return info.platformId();
        }
        int separator = matchId.indexOf('_');
        return separator > 0 ? matchId.substring(0, separator) : "";
    }

    /** PostgreSQL stocke les dates à la microseconde. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
