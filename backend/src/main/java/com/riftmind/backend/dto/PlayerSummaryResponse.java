package com.riftmind.backend.dto;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.riftmind.backend.model.MatchParticipant;

/**
 * Résumé des dernières parties d'un joueur ({@code GET /api/players/{puuid}/summary}), hors remakes, toutes files
 * confondues. Les valeurs ne sont pas arrondies.
 *
 * @param games      parties prises en compte (au plus 20, moins si le joueur en a moins en base)
 * @param winrate    pourcentage de victoires, de 0 à 100 (0 sans partie)
 * @param avgKills   éliminations par partie
 * @param avgDeaths  morts par partie
 * @param avgAssists assistances par partie
 * @param kda        (éliminations + assistances) / morts sur ces parties, éliminations + assistances sans aucune mort
 * @param roles      rôles joués, du plus fréquent au moins fréquent. Les parties sans rôle (ARAM, Arena…) n'y
 *                   figurent pas
 */
public record PlayerSummaryResponse(
        int games,
        int wins,
        int losses,
        double winrate,
        double avgKills,
        double avgDeaths,
        double avgAssists,
        double kda,
        List<RoleResponse> roles) {

    /** Ordre d'affichage des rôles à égalité de parties. */
    private static final List<String> ROLE_ORDER = List.of("TOP", "JUNGLE", "MIDDLE", "BOTTOM", "UTILITY");

    /**
     * Nombre de parties jouées à un rôle.
     *
     * @param position {@code TOP}, {@code JUNGLE}, {@code MIDDLE}, {@code BOTTOM} ou {@code UTILITY}
     */
    public record RoleResponse(String position, int games) {
    }

    public static PlayerSummaryResponse of(List<MatchParticipant> games) {
        int count = games.size();
        if (count == 0) {
            return new PlayerSummaryResponse(0, 0, 0, 0, 0, 0, 0, 0, List.of());
        }
        int wins = (int) games.stream().filter(MatchParticipant::isWin).count();
        int kills = games.stream().mapToInt(MatchParticipant::getKills).sum();
        int deaths = games.stream().mapToInt(MatchParticipant::getDeaths).sum();
        int assists = games.stream().mapToInt(MatchParticipant::getAssists).sum();
        return new PlayerSummaryResponse(count, wins, count - wins, 100.0 * wins / count,
                (double) kills / count, (double) deaths / count, (double) assists / count,
                ChampionStatsResponse.kda(kills, deaths, assists), roles(games));
    }

    private static List<RoleResponse> roles(List<MatchParticipant> games) {
        Map<String, Long> byPosition = games.stream()
                .map(MatchParticipant::getPosition)
                .filter(position -> position != null && !position.isBlank())
                .collect(Collectors.groupingBy(position -> position, Collectors.counting()));
        return byPosition.entrySet().stream()
                .map(entry -> new RoleResponse(entry.getKey(), Math.toIntExact(entry.getValue())))
                .sorted(Comparator.comparingInt(RoleResponse::games).reversed()
                        .thenComparingInt(role -> rank(role.position())))
                .toList();
    }

    private static int rank(String position) {
        int index = ROLE_ORDER.indexOf(position);
        return index >= 0 ? index : ROLE_ORDER.size();
    }
}
