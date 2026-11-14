package com.riftmind.backend.model;

import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;

/**
 * Rang converti en un seul nombre, pour comparer deux rangs et les placer sur un même axe (graphique d'évolution,
 * peak). Riot ne fournit pas le MMR : c'est l'équivalent en LP cumulés depuis Fer IV 0 LP.
 *
 * <ul>
 *   <li>Fer à Diamant : 400 par palier, 100 par division (IV, III, II, I), plus les LP. Ex. Or II 45 LP =
 *       3 × 400 + 2 × 100 + 45 = 1 445.</li>
 *   <li>Maître, Grand Maître et Challenger partagent la même échelle de LP (ce sont des seuils de LP du même
 *       classement) : 2 800 + LP. Ex. Challenger 1 234 LP = 4 034.</li>
 * </ul>
 * Un palier inconnu (ajouté par Riot depuis) n'a pas de score.
 */
public final class RankScore {

    /** Paliers à divisions, du plus bas au plus haut. */
    static final List<String> DIVIDED_TIERS =
            List.of("IRON", "BRONZE", "SILVER", "GOLD", "PLATINUM", "EMERALD", "DIAMOND");
    static final List<String> APEX_TIERS = List.of("MASTER", "GRANDMASTER", "CHALLENGER");
    /** Divisions, de la plus basse à la plus haute. */
    static final List<String> DIVISIONS = List.of("IV", "III", "II", "I");

    public static final int TIER_SIZE = 400;
    public static final int DIVISION_SIZE = 100;
    /** Score de Maître 0 LP, et de tous les paliers au-dessus. */
    public static final int APEX_BASE = DIVIDED_TIERS.size() * TIER_SIZE;

    private RankScore() {
    }

    /**
     * @param tier ex. {@code GOLD} (casse ignorée)
     * @param rank division, ex. {@code II} ; ignorée à partir de Maître
     * @return le score, ou vide pour un palier ou une division inconnus
     */
    public static OptionalInt of(String tier, String rank, int leaguePoints) {
        if (tier == null) {
            return OptionalInt.empty();
        }
        String upperTier = tier.toUpperCase(Locale.ROOT);
        if (APEX_TIERS.contains(upperTier)) {
            return OptionalInt.of(APEX_BASE + leaguePoints);
        }
        int tierIndex = DIVIDED_TIERS.indexOf(upperTier);
        int division = rank == null ? -1 : DIVISIONS.indexOf(rank.toUpperCase(Locale.ROOT));
        if (tierIndex < 0 || division < 0) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(tierIndex * TIER_SIZE + division * DIVISION_SIZE + leaguePoints);
    }
}
