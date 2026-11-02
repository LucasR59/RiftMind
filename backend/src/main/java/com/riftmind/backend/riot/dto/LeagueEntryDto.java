package com.riftmind.backend.riot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Rang d'un joueur dans une file classée, renvoyé par League-V4 ({@code /lol/league/v4/entries/by-puuid/{puuid}},
 * une liste avec une entrée par file, vide si le joueur n'est pas classé). Exemple :
 * {@code src/test/resources/riot/league-entries.json}.
 *
 * <p>Seuls les champs utiles sont lus : {@code leagueId}, {@code veteran}, {@code inactive}, {@code freshBlood} et
 * {@code miniSeries} sont ignorés.
 *
 * @param queueType    file classée, ex. {@code RANKED_SOLO_5x5} (voir {@link com.riftmind.backend.riot.RankedQueue})
 * @param tier         palier, de {@code IRON} à {@code CHALLENGER}
 * @param rank         division, de {@code IV} à {@code I} ({@code I} à partir de {@code MASTER})
 * @param leaguePoints points de ligue (LP)
 * @param wins         victoires de la saison dans cette file
 * @param losses       défaites de la saison dans cette file
 * @param hotStreak    au moins 3 victoires d'affilée
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LeagueEntryDto(String queueType, String tier, String rank, int leaguePoints, int wins, int losses,
        boolean hotStreak) {
}
