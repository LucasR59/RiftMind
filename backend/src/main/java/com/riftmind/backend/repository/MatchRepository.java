package com.riftmind.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.riftmind.backend.model.Match;
import com.riftmind.backend.model.MatchHeader;

/** Accès à la table {@code match}. La clé est l'identifiant Riot du match. */
public interface MatchRepository extends JpaRepository<Match, String> {

    /**
     * Parmi ces identifiants, ceux déjà en base. Ne lit que la clé primaire : la réponse brute ({@code raw}) n'est pas
     * chargée.
     */
    @Query("select m.matchId from Match m where m.matchId in :matchIds")
    Set<String> findExistingIds(Collection<String> matchIds);

    /** En-têtes de ces parties, sans la réponse brute. */
    @Query("""
            select new com.riftmind.backend.model.MatchHeader(
                m.matchId, m.platform, m.queueId, m.gameVersion, m.gameStart, m.durationSeconds, m.remake)
            from Match m where m.matchId in :matchIds
            """)
    List<MatchHeader> findHeaders(Collection<String> matchIds);

    /** En-tête d'une partie, sans la réponse brute. */
    default Optional<MatchHeader> findHeader(String matchId) {
        return findHeaders(List.of(matchId)).stream().findFirst();
    }
}
