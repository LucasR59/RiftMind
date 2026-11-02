package com.riftmind.backend.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import com.riftmind.backend.model.Player;
import com.riftmind.backend.riot.Platform;

/** Accès à la table {@code player}. La clé est le PUUID. */
public interface PlayerRepository extends JpaRepository<Player, String> {

    /**
     * Joueurs portant ce Riot ID sur cette plateforme, sans tenir compte de la casse. Le plus récemment rafraîchi
     * vient en premier.
     *
     * <p>Il peut y en avoir plusieurs : un Riot ID libéré puis repris reste sur l'ancienne ligne jusqu'à son
     * rafraîchissement.
     *
     * <p>La requête est écrite à la main : les méthodes dérivées {@code ...IgnoreCase} utilisent {@code upper()}, qui
     * n'utiliserait pas l'index {@code player_riot_id_idx (platform, lower(game_name), lower(tag_line))}.
     */
    @Query("""
            select p from Player p
            where p.platform = :platform
              and lower(p.gameName) = lower(:gameName)
              and lower(p.tagLine) = lower(:tagLine)
            order by p.lastRefreshedAt desc
            """)
    List<Player> findAllByRiotId(Platform platform, String gameName, String tagLine);

    /**
     * Réserve la mise à jour du joueur : passe {@code last_refreshed_at} à {@code now}, seulement si la dernière mise à
     * jour date d'avant {@code readyBefore} (fin du délai minimum). Une seule requête SQL : deux mises à jour
     * simultanées ne peuvent pas réussir toutes les deux.
     *
     * @return 1 si la mise à jour est réservée, 0 si le délai n'est pas écoulé (ou si le joueur n'existe pas)
     */
    @Modifying
    @Transactional
    @Query("""
            update Player p set p.lastRefreshedAt = :now
            where p.puuid = :puuid and p.lastRefreshedAt <= :readyBefore
            """)
    int claimRefresh(String puuid, Instant now, Instant readyBefore);

    /**
     * Annule une réservation de {@link #claimRefresh} après un échec : remet {@code previous}, seulement si
     * {@code last_refreshed_at} vaut encore {@code claimedAt}. Le joueur peut alors réessayer sans attendre.
     */
    @Modifying
    @Transactional
    @Query("""
            update Player p set p.lastRefreshedAt = :previous
            where p.puuid = :puuid and p.lastRefreshedAt = :claimedAt
            """)
    int releaseRefresh(String puuid, Instant claimedAt, Instant previous);

    /**
     * Supprime le joueur en une requête. Ses rangs sont supprimés par la base ({@code ON DELETE CASCADE}).
     *
     * @return 1 si le joueur existait, 0 sinon
     */
    @Modifying
    @Query("delete from Player p where p.puuid = :puuid")
    int deleteByPuuid(String puuid);

    /** Joueur le plus récemment rafraîchi avec ce Riot ID (voir {@link #findAllByRiotId}). */
    default Optional<Player> findByRiotId(Platform platform, String gameName, String tagLine) {
        return findAllByRiotId(platform, gameName, tagLine).stream().findFirst();
    }
}
