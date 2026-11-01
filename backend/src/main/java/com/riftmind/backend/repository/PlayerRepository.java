package com.riftmind.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

    /** Joueur le plus récemment rafraîchi avec ce Riot ID (voir {@link #findAllByRiotId}). */
    default Optional<Player> findByRiotId(Platform platform, String gameName, String tagLine) {
        return findAllByRiotId(platform, gameName, tagLine).stream().findFirst();
    }
}
