package com.riftmind.backend.dto;

import com.riftmind.backend.model.LeagueEntry;

/**
 * Rang d'un joueur dans une file classée, inclus dans {@link PlayerResponse}.
 *
 * @param queueType    {@code RANKED_SOLO_5x5} (SoloQ) ou {@code RANKED_FLEX_SR} (Flex), comme chez Riot
 * @param tier         palier, de {@code IRON} à {@code CHALLENGER}
 * @param rank         division, de {@code IV} à {@code I}. Toujours {@code I} à partir de {@code MASTER} : ne pas
 *                     l'afficher pour ces paliers
 * @param leaguePoints points de ligue (LP)
 * @param wins         victoires de la saison dans cette file
 * @param losses       défaites de la saison dans cette file
 * @param hotStreak    au moins 3 victoires d'affilée
 */
public record LeagueEntryResponse(
        String queueType,
        String tier,
        String rank,
        int leaguePoints,
        int wins,
        int losses,
        boolean hotStreak) {

    public static LeagueEntryResponse from(LeagueEntry entry) {
        return new LeagueEntryResponse(entry.getQueueType().name(), entry.getTier(), entry.getRank(),
                entry.getLeaguePoints(), entry.getWins(), entry.getLosses(), entry.isHotStreak());
    }
}
