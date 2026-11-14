package com.riftmind.backend.dto;

import java.time.Instant;

import com.riftmind.backend.model.RankSnapshot;

/**
 * Rang d'un joueur à un instant donné, point du graphique d'évolution ({@link RankHistoryResponse}).
 *
 * @param recordedAt   instant où Riot a renvoyé ce rang
 * @param tier         palier, de {@code IRON} à {@code CHALLENGER}
 * @param rank         division, de {@code IV} à {@code I} ({@code I} à partir de {@code MASTER} : ne pas l'afficher)
 * @param leaguePoints points de ligue (LP)
 * @param wins         victoires de la saison dans cette file, à cet instant
 * @param losses       défaites de la saison dans cette file, à cet instant
 * @param score        rang converti en LP cumulés depuis Fer IV 0 LP ({@link com.riftmind.backend.model.RankScore}) :
 *                     l'axe vertical du graphique. Maître 0 LP = 2 800
 */
public record RankPointResponse(
        Instant recordedAt,
        String tier,
        String rank,
        int leaguePoints,
        int wins,
        int losses,
        int score) {

    /** Point d'un rang dont le palier est connu (le score est fourni par l'appelant). */
    public static RankPointResponse from(RankSnapshot snapshot, int score) {
        return new RankPointResponse(snapshot.getRecordedAt(), snapshot.getTier(), snapshot.getRank(),
                snapshot.getLeaguePoints(), snapshot.getWins(), snapshot.getLosses(), score);
    }
}
