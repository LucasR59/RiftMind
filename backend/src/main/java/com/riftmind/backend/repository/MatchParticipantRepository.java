package com.riftmind.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.riftmind.backend.model.MatchParticipant;

/** Accès à la table {@code match_participant}. La clé est le couple (match, numéro du participant). */
public interface MatchParticipantRepository extends JpaRepository<MatchParticipant, MatchParticipant.Key> {

    /** Les joueurs d'une partie, dans l'ordre de Riot (équipe bleue puis rouge). */
    List<MatchParticipant> findByMatchIdOrderByParticipantId(String matchId);
}
