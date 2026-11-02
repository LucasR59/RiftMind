package com.riftmind.backend.service;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.riftmind.backend.repository.MatchParticipantRepository;
import com.riftmind.backend.repository.MatchRepository;
import com.riftmind.backend.repository.PlayerRepository;

/**
 * Suppression de toutes les données d'un joueur à partir de son PUUID (tâche 5.8), pour traiter les demandes de
 * suppression (RGPD) que Riot transmet aux développeurs (docs/riot-api.md §4).
 *
 * <p>Ce qui est supprimé :
 * <ul>
 *   <li>le joueur ({@code player}) et ses rangs ({@code league_entry}, par {@code ON DELETE CASCADE}) ;</li>
 *   <li>ses participations ({@code match_participant}) : ses parties restent, avec les autres joueurs seulement ;</li>
 *   <li>la réponse brute ({@code match.raw}) de ses parties, qui contient son PUUID et son Riot ID.</li>
 * </ul>
 * Rien n'est demandé à Riot. Si le joueur est recherché à nouveau plus tard, il sera récupéré comme un nouveau joueur.
 */
@Service
public class PlayerDataDeletionService {

    private static final Logger log = LoggerFactory.getLogger(PlayerDataDeletionService.class);

    private final PlayerRepository playerRepository;
    private final MatchRepository matchRepository;
    private final MatchParticipantRepository participantRepository;

    public PlayerDataDeletionService(PlayerRepository playerRepository, MatchRepository matchRepository,
            MatchParticipantRepository participantRepository) {
        this.playerRepository = playerRepository;
        this.matchRepository = matchRepository;
        this.participantRepository = participantRepository;
    }

    /**
     * Bilan d'une suppression.
     *
     * @param players        joueurs supprimés (0 ou 1 par PUUID : un joueur peut n'avoir que des participations)
     * @param participations participations supprimées
     * @param rawCleared     parties dont la réponse brute a été effacée
     */
    public record DeletionResult(int players, int participations, int rawCleared) {

        DeletionResult plus(DeletionResult other) {
            return new DeletionResult(players + other.players, participations + other.participations,
                    rawCleared + other.rawCleared);
        }
    }

    /** Supprime toutes les données de ce PUUID, dans une seule transaction. Sans effet si rien n'est connu. */
    @Transactional
    public DeletionResult deleteAllData(String puuid) {
        // Avant la suppression des participations : c'est par elles qu'on retrouve ses parties
        int rawCleared = matchRepository.clearRawOfPlayerMatches(puuid);
        int participations = participantRepository.deleteAllByPuuid(puuid);
        int players = playerRepository.deleteByPuuid(puuid);
        DeletionResult result = new DeletionResult(players, participations, rawCleared);
        // Le PUUID n'est pas une donnée sensible en soi, mais inutile de le garder dans les logs
        log.info("Données d'un joueur supprimées : {}", result);
        return result;
    }

    /** Traite une liste de PUUID transmise par Riot, dans une seule transaction. */
    @Transactional
    public DeletionResult deleteAllData(Collection<String> puuids) {
        return puuids.stream().distinct()
                .map(this::deleteAllData)
                .reduce(new DeletionResult(0, 0, 0), DeletionResult::plus);
    }
}
