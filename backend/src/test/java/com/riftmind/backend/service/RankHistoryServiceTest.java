package com.riftmind.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.riftmind.backend.TestcontainersConfiguration;
import com.riftmind.backend.dto.RankHistoryResponse;
import com.riftmind.backend.dto.RankPointResponse;
import com.riftmind.backend.exception.PlayerNotFoundException;
import com.riftmind.backend.model.Player;
import com.riftmind.backend.model.RankSnapshot;
import com.riftmind.backend.repository.PlayerRepository;
import com.riftmind.backend.repository.RankSnapshotRepository;
import com.riftmind.backend.riot.Platform;
import com.riftmind.backend.riot.RankedQueue;
import com.riftmind.backend.riot.RiotFixtures;

/** Évolution du rang et peak (tâche 7.10), sur un vrai PostgreSQL. Chaque test est annulé à la fin. */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class RankHistoryServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-01T18:00:00Z");
    private static final String PUUID = RiotFixtures.PUUID;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private RankSnapshotRepository snapshotRepository;

    private RankHistoryService service;

    @BeforeEach
    void setUp() {
        service = new RankHistoryService(playerRepository, snapshotRepository);
        playerRepository.save(new Player(PUUID, "Joueur Test", "EUW", Platform.EUW1, 512, 7, T0));
    }

    /** Enregistre un rang en SoloQ, {@code hours} heures après T0. */
    private void solo(int hours, String tier, String rank, int leaguePoints) {
        snapshotRepository.save(new RankSnapshot(PUUID, RankedQueue.RANKED_SOLO_5x5, tier, rank, leaguePoints, 10,
                10, T0.plus(Duration.ofHours(hours))));
    }

    @Test
    void renvoieLesRangsDuPlusAncienAuPlusRecentAvecLeurScore() {
        solo(2, "GOLD", "I", 10);
        solo(0, "GOLD", "II", 45);
        solo(1, "GOLD", "II", 80);

        RankHistoryResponse solo = service.getHistory(PUUID).getFirst();

        assertThat(solo.queueType()).isEqualTo("RANKED_SOLO_5x5");
        assertThat(solo.points()).extracting(RankPointResponse::score).containsExactly(1445, 1480, 1510);
        assertThat(solo.points().getFirst().recordedAt()).isEqualTo(T0);
    }

    @Test
    void lePeakEstLeMeilleurRangPasLeDernier() {
        solo(0, "PLATINUM", "IV", 20);
        solo(1, "EMERALD", "III", 60);   // peak
        solo(2, "EMERALD", "IV", 40);
        solo(3, "PLATINUM", "I", 75);

        RankPointResponse peak = service.getHistory(PUUID).getFirst().peak();

        assertThat(peak.tier()).isEqualTo("EMERALD");
        assertThat(peak.rank()).isEqualTo("III");
        assertThat(peak.leaguePoints()).isEqualTo(60);
        assertThat(peak.recordedAt()).isEqualTo(T0.plus(Duration.ofHours(1)));
    }

    @Test
    void aEgaliteLePeakEstLePremierAtteint() {
        solo(0, "GOLD", "I", 50);
        solo(1, "GOLD", "I", 20);
        solo(2, "GOLD", "I", 50);

        assertThat(service.getHistory(PUUID).getFirst().peak().recordedAt()).isEqualTo(T0);
    }

    @Test
    void chaqueFileASonHistoriqueEtSonPeakSoloQPuisFlex() {
        snapshotRepository.save(new RankSnapshot(PUUID, RankedQueue.RANKED_FLEX_SR, "DIAMOND", "IV", 10, 3, 1, T0));
        solo(0, "SILVER", "II", 30);

        List<RankHistoryResponse> history = service.getHistory(PUUID);

        assertThat(history).extracting(RankHistoryResponse::queueType)
                .containsExactly("RANKED_SOLO_5x5", "RANKED_FLEX_SR");
        assertThat(history.get(1).peak().tier()).isEqualTo("DIAMOND");
        assertThat(history.get(0).peak().tier()).isEqualTo("SILVER");
    }

    @Test
    void unPalierInconnuEstIgnore() {
        solo(0, "GOLD", "IV", 0);
        solo(1, "WOOD", "I", 999);

        RankHistoryResponse solo = service.getHistory(PUUID).getFirst();

        assertThat(solo.points()).hasSize(1);
        assertThat(solo.peak().tier()).isEqualTo("GOLD");
    }

    @Test
    void renvoieAuPlusLesDerniersPointsMaisLePeakDeToutLHistorique() {
        solo(0, "CHALLENGER", "I", 1500); // peak, plus ancien que les points renvoyés
        for (int i = 1; i <= RankHistoryService.MAX_POINTS + 10; i++) {
            solo(i, "GOLD", "IV", i % 100);
        }

        RankHistoryResponse solo = service.getHistory(PUUID).getFirst();

        assertThat(solo.points()).hasSize(RankHistoryService.MAX_POINTS);
        assertThat(solo.points().getLast().recordedAt())
                .isEqualTo(T0.plus(Duration.ofHours(RankHistoryService.MAX_POINTS + 10)));
        assertThat(solo.peak().tier()).isEqualTo("CHALLENGER");
    }

    @Test
    void unJoueurJamaisClasseNAPasDHistorique() {
        assertThat(service.getHistory(PUUID)).isEmpty();
    }

    @Test
    void unPuuidInconnuEstUneErreur404() {
        assertThatThrownBy(() -> service.getHistory(RiotFixtures.puuid(2)))
                .isInstanceOf(PlayerNotFoundException.class);
    }
}
