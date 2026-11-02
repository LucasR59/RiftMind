package com.riftmind.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.riftmind.backend.TestMatches;
import com.riftmind.backend.TestcontainersConfiguration;
import com.riftmind.backend.dto.CompactParticipantResponse;
import com.riftmind.backend.dto.MatchPageResponse;
import com.riftmind.backend.dto.MatchSummaryResponse;
import com.riftmind.backend.exception.InvalidRequestException;
import com.riftmind.backend.exception.PlayerNotFoundException;
import com.riftmind.backend.model.Match;
import com.riftmind.backend.model.Player;
import com.riftmind.backend.repository.MatchParticipantRepository;
import com.riftmind.backend.repository.MatchRepository;
import com.riftmind.backend.repository.PlayerRepository;
import com.riftmind.backend.riot.Platform;
import com.riftmind.backend.riot.RiotFixtures;

/**
 * Lecture des parties enregistrées (phase 5), sur un vrai PostgreSQL. Chaque test est annulé à la fin.
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, MatchQueryService.class})
class MatchQueryServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-01T12:00:00Z");

    @Autowired
    private MatchQueryService service;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private MatchParticipantRepository participantRepository;

    @BeforeEach
    void setUp() {
        playerRepository.save(new Player(RiotFixtures.PUUID, RiotFixtures.GAME_NAME, RiotFixtures.TAG_LINE,
                Platform.EUW1, 512, 7, T0));
    }

    /** La partie n°{@code n} commence {@code n} heures après T0 : plus n est grand, plus elle est récente. */
    private TestMatches game(int n) {
        return TestMatches.match("EUW1_%d".formatted(n)).start(T0.plus(Duration.ofHours(n)));
    }

    private void save(TestMatches... games) {
        for (TestMatches game : games) {
            game.save(matchRepository, participantRepository);
        }
    }

    // --- 5.1 : historique ---

    @Test
    void lHistoriqueVaDuPlusRecentAuPlusAncien() {
        save(game(1), game(3), game(2));

        MatchPageResponse history = service.getHistory(RiotFixtures.PUUID, 0, 10, null);

        assertThat(history.items()).extracting(MatchSummaryResponse::matchId)
                .containsExactly("EUW1_3", "EUW1_2", "EUW1_1");
        assertThat(history.hasMore()).isFalse();
        assertThat(history.page()).isZero();
        assertThat(history.size()).isEqualTo(10);
    }

    @Test
    void chaquePartieContientLeResumeDuJoueurEtLesDixJoueurs() {
        save(game(1).queue(440).duration(1463).me(429, "Kalista", "BOTTOM", true, 6, 4, 3, 250, 19708));

        MatchSummaryResponse match = service.getHistory(RiotFixtures.PUUID, 0, 10, null).items().getFirst();

        assertThat(match.queueId()).isEqualTo(440);
        assertThat(match.durationSeconds()).isEqualTo(1463);
        assertThat(match.gameStart()).isEqualTo(T0.plus(Duration.ofHours(1)));
        assertThat(match.gameVersion()).isEqualTo("16.21.834.2754");
        assertThat(match.remake()).isFalse();
        assertThat(match.player().puuid()).isEqualTo(RiotFixtures.PUUID);
        assertThat(match.player().championName()).isEqualTo("Kalista");
        assertThat(match.player().kills()).isEqualTo(6);
        assertThat(match.player().cs()).isEqualTo(250);
        assertThat(match.player().items()).containsExactly(1001, 3006, 0, 0, 0, 0, 3340);
        assertThat(match.player().summonerSpells()).containsExactly(4, 14);
        assertThat(match.player().keystone()).isEqualTo(8112);
        assertThat(match.participants()).hasSize(10)
                .extracting(CompactParticipantResponse::puuid).first().isEqualTo(RiotFixtures.PUUID);
        assertThat(match.participants()).extracting(CompactParticipantResponse::teamId)
                .containsExactly(100, 100, 100, 100, 100, 200, 200, 200, 200, 200);
    }

    @Test
    void lHistoriqueEstPagine() {
        save(game(1), game(2), game(3), game(4), game(5));

        MatchPageResponse first = service.getHistory(RiotFixtures.PUUID, 0, 2, null);
        MatchPageResponse second = service.getHistory(RiotFixtures.PUUID, 1, 2, null);
        MatchPageResponse last = service.getHistory(RiotFixtures.PUUID, 2, 2, null);
        MatchPageResponse beyond = service.getHistory(RiotFixtures.PUUID, 3, 2, null);

        assertThat(first.items()).extracting(MatchSummaryResponse::matchId).containsExactly("EUW1_5", "EUW1_4");
        assertThat(first.hasMore()).isTrue();
        assertThat(second.items()).extracting(MatchSummaryResponse::matchId).containsExactly("EUW1_3", "EUW1_2");
        assertThat(second.hasMore()).isTrue();
        assertThat(last.items()).extracting(MatchSummaryResponse::matchId).containsExactly("EUW1_1");
        assertThat(last.hasMore()).isFalse();
        assertThat(beyond.items()).isEmpty();
        assertThat(beyond.hasMore()).isFalse();
    }

    @Test
    void lHistoriqueSeFiltreParFile() {
        save(game(1).queue(420), game(2).queue(440), game(3).queue(420), game(4).queue(450));

        assertThat(service.getHistory(RiotFixtures.PUUID, 0, 10, 420).items())
                .extracting(MatchSummaryResponse::matchId).containsExactly("EUW1_3", "EUW1_1");
        assertThat(service.getHistory(RiotFixtures.PUUID, 0, 10, 440).items())
                .extracting(MatchSummaryResponse::matchId).containsExactly("EUW1_2");
        assertThat(service.getHistory(RiotFixtures.PUUID, 0, 10, 900).items()).isEmpty();
    }

    @Test
    void lesRemakesSontDansLHistoriqueMarques() {
        save(game(1), game(2).remake());

        assertThat(service.getHistory(RiotFixtures.PUUID, 0, 10, null).items())
                .extracting(MatchSummaryResponse::matchId, MatchSummaryResponse::remake)
                .containsExactly(
                        tuple("EUW1_2", true),
                        tuple("EUW1_1", false));
    }

    @Test
    void lesPartiesPersonnaliseesNApparaissentPas() {
        save(game(1));
        // Marqueur d'une partie personnalisée (tâche 4.6) : pas de joueurs
        matchRepository.save(new Match("EUW1_2", "EUW1", 0, "16.21", T0.plus(Duration.ofHours(2)), 600, false, null,
                T0));

        assertThat(service.getHistory(RiotFixtures.PUUID, 0, 10, null).items())
                .extracting(MatchSummaryResponse::matchId).containsExactly("EUW1_1");
    }

    @Test
    void unJoueurSansPartieAUnHistoriqueVide() {
        MatchPageResponse history = service.getHistory(RiotFixtures.PUUID, 0, 10, null);

        assertThat(history.items()).isEmpty();
        assertThat(history.hasMore()).isFalse();
    }

    @Test
    void unPuuidInconnuDonneJoueurIntrouvable() {
        assertThatThrownBy(() -> service.getHistory(RiotFixtures.puuid(9), 0, 10, null))
                .isInstanceOf(PlayerNotFoundException.class)
                .hasMessageContaining("recherchez-le d'abord par son Riot ID");
    }

    @ParameterizedTest
    @CsvSource({"-1, 10", "0, 0", "0, 51"})
    void refuseUnePageOuUneTailleHorsLimites(int page, int size) {
        assertThatThrownBy(() -> service.getHistory(RiotFixtures.PUUID, page, size, null))
                .isInstanceOf(InvalidRequestException.class);
    }
}
