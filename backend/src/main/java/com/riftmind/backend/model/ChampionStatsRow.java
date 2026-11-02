package com.riftmind.backend.model;

/**
 * Totaux d'un joueur sur un champion, calculés en SQL ({@code GROUP BY champion_id}). Les moyennes et ratios sont
 * calculés à partir de ces totaux (voir {@code ChampionStatsResponse}).
 *
 * @param games           parties jouées (hors remakes)
 * @param durationSeconds durée cumulée de ces parties
 * @param damage          dégâts cumulés infligés aux champions
 */
public record ChampionStatsRow(int championId, String championName, long games, long wins, long kills, long deaths,
        long assists, long cs, long durationSeconds, long damage) {
}
