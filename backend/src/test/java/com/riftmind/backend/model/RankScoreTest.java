package com.riftmind.backend.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Rang converti en LP cumulés depuis Fer IV 0 LP (tâche 7.10). */
class RankScoreTest {

    @ParameterizedTest
    @CsvSource({
            "IRON, IV, 0, 0",
            "IRON, I, 99, 399",
            "BRONZE, IV, 0, 400",
            "GOLD, II, 45, 1445",
            "EMERALD, IV, 0, 2000",
            "DIAMOND, I, 99, 2799",
            "MASTER, I, 0, 2800",
            "GRANDMASTER, I, 350, 3150",
            "CHALLENGER, I, 1234, 4034",
            "gold, ii, 45, 1445",
    })
    void convertitUnRangEnScore(String tier, String rank, int leaguePoints, int score) {
        assertThat(RankScore.of(tier, rank, leaguePoints)).hasValue(score);
    }

    @Test
    void chaquePalierEtDivisionEstAuDessusDuPrecedent() {
        List<int[]> scores = RankScore.DIVIDED_TIERS.stream()
                .flatMap(tier -> RankScore.DIVISIONS.stream()
                        .map(division -> new int[] {RankScore.of(tier, division, 0).orElseThrow(),
                                RankScore.of(tier, division, 99).orElseThrow()}))
                .toList();

        for (int i = 1; i < scores.size(); i++) {
            assertThat(scores.get(i)[0]).isGreaterThan(scores.get(i - 1)[1]);
        }
        assertThat(RankScore.of("MASTER", "I", 0).orElseThrow()).isGreaterThan(scores.getLast()[1]);
    }

    @Test
    void lesPaliersApexPartagentLaMemeEchelle() {
        // Un Maître à 500 LP est au-dessus d'un Grand Maître à 300 LP : seuls les LP comptent
        assertThat(RankScore.of("MASTER", "I", 500).orElseThrow())
                .isGreaterThan(RankScore.of("GRANDMASTER", "I", 300).orElseThrow());
    }

    @Test
    void unPalierOuUneDivisionInconnusNOntPasDeScore() {
        assertThat(RankScore.of("WOOD", "I", 10)).isEqualTo(OptionalInt.empty());
        assertThat(RankScore.of("GOLD", "V", 10)).isEmpty();
        assertThat(RankScore.of("GOLD", null, 10)).isEmpty();
        assertThat(RankScore.of(null, "I", 10)).isEmpty();
    }
}
