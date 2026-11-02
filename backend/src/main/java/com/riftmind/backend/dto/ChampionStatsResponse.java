package com.riftmind.backend.dto;

import com.riftmind.backend.model.ChampionStatsRow;

/**
 * Statistiques d'un joueur sur un champion ({@code GET /api/players/{puuid}/champions}), hors remakes. Les valeurs
 * ne sont pas arrondies : le front choisit l'affichage.
 *
 * @param championId    {@code key} du champion dans {@code champion.json}
 * @param championName  {@code id} du champion, ex. {@code KogMaw}
 * @param games         parties jouées
 * @param winrate       pourcentage de victoires, de 0 à 100
 * @param avgKills      éliminations par partie
 * @param avgDeaths     morts par partie
 * @param avgAssists    assistances par partie
 * @param kda           (éliminations + assistances) / morts, sur l'ensemble des parties. Sans aucune mort : éliminations
 *                      + assistances (affiché « Parfait » par la plupart des sites)
 * @param csPerMinute   CS total / durée totale en minutes
 * @param avgDamage     dégâts infligés aux champions par partie
 */
public record ChampionStatsResponse(
        int championId,
        String championName,
        long games,
        long wins,
        long losses,
        double winrate,
        double avgKills,
        double avgDeaths,
        double avgAssists,
        double kda,
        double csPerMinute,
        double avgDamage) {

    public static ChampionStatsResponse from(ChampionStatsRow row) {
        double games = row.games();
        double minutes = row.durationSeconds() / 60.0;
        return new ChampionStatsResponse(row.championId(), row.championName(), row.games(), row.wins(),
                row.games() - row.wins(),
                100.0 * row.wins() / games,
                row.kills() / games,
                row.deaths() / games,
                row.assists() / games,
                kda(row.kills(), row.deaths(), row.assists()),
                minutes > 0 ? row.cs() / minutes : 0,
                row.damage() / games);
    }

    /** (K + A) / D, ou K + A sans aucune mort. */
    static double kda(long kills, long deaths, long assists) {
        return (double) (kills + assists) / Math.max(deaths, 1);
    }
}
