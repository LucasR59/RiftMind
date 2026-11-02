package com.riftmind.backend.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import com.riftmind.backend.model.MatchParticipant;

/** Accès à la table {@code match_participant}. La clé est le couple (match, numéro du participant). */
public interface MatchParticipantRepository extends JpaRepository<MatchParticipant, MatchParticipant.Key> {

    /** Les joueurs d'une partie, dans l'ordre de Riot (équipe bleue puis rouge). */
    List<MatchParticipant> findByMatchIdOrderByParticipantId(String matchId);

    /** Les joueurs de plusieurs parties, groupés par partie puis dans l'ordre de Riot. */
    List<MatchParticipant> findByMatchIdInOrderByMatchIdAscParticipantIdAsc(Collection<String> matchIds);

    /**
     * Participations d'un joueur, de la plus récente à la plus ancienne (index
     * {@code match_participant_puuid_game_start_idx}). Une {@link Slice} lit une ligne de plus que la page pour savoir
     * s'il en reste, sans compter toutes les parties.
     */
    Slice<MatchParticipant> findByPuuidOrderByGameStartDescMatchIdDesc(String puuid, Pageable pageable);

    /** Comme {@link #findByPuuidOrderByGameStartDescMatchIdDesc}, limité à une file de jeu. */
    Slice<MatchParticipant> findByPuuidAndQueueIdOrderByGameStartDescMatchIdDesc(String puuid, int queueId,
            Pageable pageable);
}
