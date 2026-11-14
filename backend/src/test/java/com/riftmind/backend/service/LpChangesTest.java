package com.riftmind.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.riftmind.backend.model.RankSnapshot;
import com.riftmind.backend.model.RankedGame;
import com.riftmind.backend.riot.RankedQueue;
import com.riftmind.backend.riot.RiotFixtures;

/**
 * Variation de LP attribuée à une partie (tâche 7.10) : seulement quand elle est certaine. Les heures sont comptées
 * depuis T0 ; une partie dure 30 min.
 */
class LpChangesTest {

    private static final Instant T0 = Instant.parse("2026-11-01T12:00:00Z");
    private static final RankedQueue SOLO = RankedQueue.RANKED_SOLO_5x5;
    private static final RankedQueue FLEX = RankedQueue.RANKED_FLEX_SR;

    private static Instant at(double hours) {
        return T0.plus(Duration.ofMinutes(Math.round(hours * 60)));
    }

    private static RankSnapshot rank(RankedQueue queue, double hours, String tier, String division, int lp, int wins,
            int losses) {
        return new RankSnapshot(RiotFixtures.PUUID, queue, tier, division, lp, wins, losses, at(hours));
    }

    private static RankSnapshot solo(double hours, String tier, String division, int lp, int wins, int losses) {
        return rank(SOLO, hours, tier, division, lp, wins, losses);
    }

    /** Partie de 30 min commencée à {@code startHours}. */
    private static RankedGame game(String id, int queueId, double startHours, boolean win) {
        return new RankedGame(id, queueId, at(startHours), 1800, win, false);
    }

    private static RankedGame soloGame(String id, double startHours, boolean win) {
        return game(id, 420, startHours, win);
    }

    @Test
    void uneVictoireEntreDeuxRelevesRecoitLesLpGagnes() {
        var changes = LpChanges.attribute(
                List.of(solo(0, "GOLD", "II", 45, 10, 8), solo(2, "GOLD", "II", 63, 11, 8)),
                List.of(soloGame("W", 1, true)));

        assertThat(changes).containsExactlyEntriesOf(Map.of("W", 18));
    }

    @Test
    void uneDefaiteRecoitLesLpPerdus() {
        var changes = LpChanges.attribute(
                List.of(solo(0, "GOLD", "II", 45, 10, 8), solo(2, "GOLD", "II", 30, 10, 9)),
                List.of(soloGame("L", 1, false)));

        assertThat(changes).containsEntry("L", -15);
    }

    @Test
    void tientCompteDesChangementsDeDivisionEtDePalier() {
        var changes = LpChanges.attribute(
                List.of(solo(0, "DIAMOND", "I", 90, 50, 40), solo(2, "MASTER", "I", 10, 51, 40),
                        solo(4, "MASTER", "I", 0, 51, 41), solo(6, "DIAMOND", "I", 75, 51, 42)),
                List.of(soloGame("PROMO", 1, true), soloGame("ZERO", 3, false), soloGame("DEMOTE", 5, false)));

        assertThat(changes).containsEntry("PROMO", 20).containsEntry("ZERO", -10).containsEntry("DEMOTE", -25);
    }

    @Test
    void plusieursPartiesEntreDeuxRelevesNeSontPasAttribuees() {
        var changes = LpChanges.attribute(
                List.of(solo(0, "GOLD", "II", 45, 10, 8), solo(3, "GOLD", "II", 80, 12, 8)),
                List.of(soloGame("A", 1, true), soloGame("B", 2, true)));

        assertThat(changes).isEmpty();
    }

    @Test
    void unePartieAbsenteDeLaBaseNestPasDevinee() {
        var changes = LpChanges.attribute(
                List.of(solo(0, "GOLD", "II", 45, 10, 8), solo(2, "GOLD", "II", 63, 11, 8)),
                List.of());

        assertThat(changes).isEmpty();
    }

    @Test
    void unBilanDUnePartieMaisDeuxPartiesEnBaseNestPasAttribue() {
        // Incohérent (une partie ignorée par Riot ?) : on ne choisit pas
        var changes = LpChanges.attribute(
                List.of(solo(0, "GOLD", "II", 45, 10, 8), solo(3, "GOLD", "II", 63, 11, 8)),
                List.of(soloGame("A", 1, true), soloGame("B", 2, true)));

        assertThat(changes).isEmpty();
    }

    @Test
    void unRemakeEntreDeuxRelevesNeCompteJamais() {
        var changes = LpChanges.attribute(
                List.of(solo(0, "GOLD", "II", 45, 10, 8), solo(3, "GOLD", "II", 63, 11, 8)),
                List.of(new RankedGame("REMAKE", 420, at(0.5), 240, false, true), soloGame("W", 2, true)));

        assertThat(changes).containsOnlyKeys("W");
    }

    @Test
    void unResultatContraireAuBilanNEstPasAttribue() {
        var changes = LpChanges.attribute(
                List.of(solo(0, "GOLD", "II", 45, 10, 8), solo(2, "GOLD", "II", 63, 11, 8)),
                List.of(soloGame("L", 1, false)));

        assertThat(changes).isEmpty();
    }

    @Test
    void chaqueFileAvecSesPropresParties() {
        var changes = LpChanges.attribute(
                List.of(solo(0, "GOLD", "II", 45, 10, 8), solo(2, "GOLD", "II", 63, 11, 8),
                        rank(FLEX, 0, "SILVER", "I", 20, 3, 3), rank(FLEX, 2, "SILVER", "I", 2, 3, 4)),
                List.of(soloGame("SOLO", 1, true), game("FLEX", 440, 1.2, false), game("ARAM", 450, 1.1, true)));

        assertThat(changes).containsEntry("SOLO", 18).containsEntry("FLEX", -18).doesNotContainKey("ARAM");
    }

    @Test
    void uneNouvelleSaisonNestPasUnePartie() {
        // Le bilan repart de zéro : la différence n'est pas une partie
        var changes = LpChanges.attribute(
                List.of(solo(0, "PLATINUM", "I", 50, 120, 100), solo(48, "GOLD", "IV", 0, 1, 0)),
                List.of(soloGame("PLACEMENT", 47, true)));

        assertThat(changes).isEmpty();
    }

    @Test
    void desLpPerdusSansPartieNeSontAttribuesAAucunePartie() {
        // Inactivité (Diamant et plus) : les LP baissent, le bilan ne bouge pas
        var changes = LpChanges.attribute(
                List.of(solo(0, "DIAMOND", "II", 60, 30, 30), solo(200, "DIAMOND", "II", 10, 30, 30)),
                List.of(soloGame("OLD", -5, true)));

        assertThat(changes).isEmpty();
    }

    @Test
    void lesPartiesSontPlaceesParLeurFin() {
        // Commencée avant le premier relevé, finie après : elle est entre les deux relevés
        var changes = LpChanges.attribute(
                List.of(solo(1, "GOLD", "II", 45, 10, 8), solo(3, "GOLD", "II", 63, 11, 8)),
                List.of(soloGame("W", 0.75, true), soloGame("BEFORE", 0, false)));

        assertThat(changes).containsOnlyKeys("W");
    }

    @Test
    void lOrdreDesRelevesNImportePas() {
        var changes = LpChanges.attribute(
                List.of(solo(2, "GOLD", "II", 63, 11, 8), solo(0, "GOLD", "II", 45, 10, 8)),
                List.of(soloGame("W", 1, true)));

        assertThat(changes).containsEntry("W", 18);
    }
}
