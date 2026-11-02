package com.riftmind.backend.service;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.riftmind.backend.model.LeagueEntry;
import com.riftmind.backend.model.Player;
import com.riftmind.backend.repository.LeagueEntryRepository;
import com.riftmind.backend.riot.Platform;
import com.riftmind.backend.riot.RankedQueue;
import com.riftmind.backend.riot.RiotClient;
import com.riftmind.backend.riot.dto.LeagueEntryDto;

/**
 * Rangs classés des joueurs (tâche 3.2) : lecture chez Riot (League-V4) et mise à jour en base.
 *
 * <p>L'appel à Riot ({@link #fetch}) et l'écriture en base ({@link #save}) sont séparés : comme dans
 * {@link PlayerService}, aucune transaction n'est ouverte pendant un appel à Riot, et un appelant peut récupérer
 * toutes les données de Riot avant d'écrire quoi que ce soit.
 */
@Service
public class LeagueService {

    private static final Logger log = LoggerFactory.getLogger(LeagueService.class);

    static final String ENTRIES_BY_PUUID = "/lol/league/v4/entries/by-puuid/{puuid}";
    /** SQLState de PostgreSQL pour une violation de contrainte d'unicité. */
    private static final String UNIQUE_VIOLATION = "23505";
    private static final ParameterizedTypeReference<List<LeagueEntryDto>> ENTRY_LIST =
            new ParameterizedTypeReference<>() {
            };
    /** Ordre d'affichage : SoloQ, puis Flex. */
    private static final Comparator<LeagueEntry> BY_QUEUE = Comparator.comparing(LeagueEntry::getQueueType);

    private final RiotClient riotClient;
    private final LeagueEntryRepository leagueEntryRepository;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public LeagueService(RiotClient riotClient, LeagueEntryRepository leagueEntryRepository,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.riotClient = riotClient;
        this.leagueEntryRepository = leagueEntryRepository;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Rangs du joueur en base, SoloQ puis Flex. Liste vide si le joueur n'est pas classé (ou inconnu). */
    public List<LeagueEntry> findByPuuid(String puuid) {
        return leagueEntryRepository.findByPuuid(puuid).stream().sorted(BY_QUEUE).toList();
    }

    /** Récupère les rangs chez Riot et les enregistre (bouton « Mettre à jour », tâche 5.5). */
    public List<LeagueEntry> refresh(Player player) {
        List<LeagueEntryDto> entries = fetch(player.getPlatform(), player.getPuuid());
        // PostgreSQL stocke les dates à la microseconde : on tronque pour renvoyer la même valeur qu'à la relecture
        return save(player.getPuuid(), entries, clock.instant().truncatedTo(ChronoUnit.MICROS));
    }

    /**
     * Rangs du joueur chez Riot (League-V4, sur l'hôte de sa plateforme). Rien n'est écrit en base.
     *
     * @return une entrée par file classée jouée cette saison, liste vide si le joueur n'est pas classé
     * @throws com.riftmind.backend.riot.RiotApiException si Riot est indisponible ou refuse l'appel
     */
    public List<LeagueEntryDto> fetch(Platform platform, String puuid) {
        List<LeagueEntryDto> entries = riotClient.get(platform, ENTRIES_BY_PUUID, ENTRY_LIST, puuid);
        return entries != null ? entries : List.of();
    }

    /**
     * Remplace en base les rangs du joueur par ceux renvoyés par Riot, dans une seule transaction :
     * <ul>
     *   <li>une file déjà en base est mise à jour, une nouvelle file est ajoutée ;</li>
     *   <li>une file absente de la réponse est supprimée (le joueur n'y est plus classé, ex. après la fin de
     *       saison) ;</li>
     *   <li>les files que RiftMind n'affiche pas (voir {@link RankedQueue#fromRiot}) sont ignorées.</li>
     * </ul>
     * Le joueur doit déjà être en base (clé étrangère).
     *
     * @param fetchedAt instant de l'appel à Riot, enregistré dans {@code updated_at}
     * @return les rangs enregistrés, SoloQ puis Flex
     */
    public List<LeagueEntry> save(String puuid, List<LeagueEntryDto> entries, Instant fetchedAt) {
        Map<RankedQueue, LeagueEntryDto> byQueue = byKnownQueue(puuid, entries);
        try {
            return transaction.execute(status -> replaceEntries(puuid, byQueue, fetchedAt));
        } catch (DataIntegrityViolationException e) {
            if (!isDuplicateKey(e)) {
                throw e;
            }
            // Deux mises à jour simultanées du même joueur : l'autre a inséré la même file en premier, avec des
            // données tout aussi récentes
            log.debug("Rangs du joueur {} enregistrés en parallèle par une autre requête", puuid);
            return findByPuuid(puuid);
        }
    }

    /** Vrai pour une clé déjà présente (SQLState 23505), faux pour une autre contrainte (CHECK, clé étrangère). */
    private static boolean isDuplicateKey(DataIntegrityViolationException e) {
        return e instanceof DuplicateKeyException
                || NestedExceptionUtils.getMostSpecificCause(e) instanceof SQLException sql
                        && UNIQUE_VIOLATION.equals(sql.getSQLState());
    }

    private List<LeagueEntry> replaceEntries(String puuid, Map<RankedQueue, LeagueEntryDto> byQueue,
            Instant fetchedAt) {
        List<LeagueEntry> saved = new ArrayList<>();
        List<LeagueEntry> stale = new ArrayList<>();
        for (LeagueEntry existing : leagueEntryRepository.findByPuuid(puuid)) {
            LeagueEntryDto dto = byQueue.remove(existing.getQueueType());
            if (dto == null) {
                stale.add(existing);
            } else {
                existing.update(dto.tier(), dto.rank(), dto.leaguePoints(), dto.wins(), dto.losses(),
                        dto.hotStreak(), fetchedAt);
                saved.add(existing);
            }
        }
        byQueue.forEach((queue, dto) -> saved.add(new LeagueEntry(puuid, queue, dto.tier(), dto.rank(),
                dto.leaguePoints(), dto.wins(), dto.losses(), dto.hotStreak(), fetchedAt)));

        if (!stale.isEmpty()) {
            log.info("Joueur {} plus classé en {} : rang supprimé", puuid,
                    stale.stream().map(LeagueEntry::getQueueType).toList());
            leagueEntryRepository.deleteAll(stale);
        }
        List<LeagueEntry> result = leagueEntryRepository.saveAll(saved);
        // Écrit tout de suite pour que les erreurs (clé dupliquée…) sortent ici, déjà traduites par Spring
        leagueEntryRepository.flush();
        return result.stream().sorted(BY_QUEUE).toList();
    }

    /** Garde les files affichées par RiftMind, une entrée par file. */
    private static Map<RankedQueue, LeagueEntryDto> byKnownQueue(String puuid, List<LeagueEntryDto> entries) {
        Map<RankedQueue, LeagueEntryDto> byQueue = new EnumMap<>(RankedQueue.class);
        for (LeagueEntryDto dto : entries) {
            Optional<RankedQueue> queue = RankedQueue.fromRiot(dto.queueType());
            if (queue.isPresent()) {
                byQueue.put(queue.get(), dto);
            } else {
                log.debug("File {} ignorée pour le joueur {}", dto.queueType(), puuid);
            }
        }
        return byQueue;
    }
}
