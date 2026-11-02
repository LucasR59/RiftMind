package com.riftmind.backend.repository;

import java.util.Collection;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.riftmind.backend.model.Match;

/** Accès à la table {@code match}. La clé est l'identifiant Riot du match. */
public interface MatchRepository extends JpaRepository<Match, String> {

    /**
     * Parmi ces identifiants, ceux déjà en base. Ne lit que la clé primaire : la réponse brute ({@code raw}) n'est pas
     * chargée.
     */
    @Query("select m.matchId from Match m where m.matchId in :matchIds")
    Set<String> findExistingIds(Collection<String> matchIds);
}
