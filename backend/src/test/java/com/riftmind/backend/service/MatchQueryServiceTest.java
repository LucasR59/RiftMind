package com.riftmind.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.riftmind.backend.TestMatches;
import com.riftmind.backend.TestcontainersConfiguration;
import com.riftmind.backend.dto.ChampionStatsResponse;
import com.riftmind.backend.dto.CompactParticipantResponse;
import com.riftmind.backend.dto.MatchDetailResponse;
import com.riftmind.backend.dto.MatchPageResponse;
import com.riftmind.backend.dto.MatchSummaryResponse;
import com.riftmind.backend.dto.ParticipantResponse;
import com.riftmind.backend.dto.PlayerSummaryResponse;
import com.riftmind.backend.dto.TeamResponse;
import com.riftmind.backend.exception.InvalidRequestException;
import com.riftmind.backend.exception.MatchNotFoundException;
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

    // --- 5.2 : détail d'une partie ---

    @Test
    void leDetailContientLesDixJoueursEtLesTotauxParEquipe() {
        save(game(1).queue(420).duration(1463).me(429, "Kalista", "BOTTOM", true, 6, 4, 3, 250, 19708));

        MatchDetailResponse detail = service.getMatch("EUW1_1");

        assertThat(detail.matchId()).isEqualTo("EUW1_1");
        assertThat(detail.platform()).isEqualTo("EUW1");
        assertThat(detail.queueId()).isEqualTo(420);
        assertThat(detail.durationSeconds()).isEqualTo(1463);
        assertThat(detail.participants()).hasSize(10).extracting(ParticipantResponse::participantId)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(detail.participants().getFirst().championName()).isEqualTo("Kalista");
        // Bleue : le joueur (6/4/3, 19708 dégâts) + 4 joueurs à 1/1/1 et 10000 dégâts ; rouge : 5 joueurs à 1/1/1
        assertThat(detail.teams()).containsExactly(
                new TeamResponse(100, true, 10, 8, 7, 60000, 59708),
                new TeamResponse(200, false, 5, 5, 5, 60000, 50000));
    }

    @Test
    void unePartieInconnueDonnePartieIntrouvable() {
        assertThatThrownBy(() -> service.getMatch("EUW1_404"))
                .isInstanceOf(MatchNotFoundException.class).hasMessage("Partie « EUW1_404 » introuvable.");
    }

    @Test
    void unePartiePersonnaliseeDonnePartieIntrouvable() {
        matchRepository.save(new Match("EUW1_2", "EUW1", 0, "16.21", T0, 600, false, null, T0));

        assertThatThrownBy(() -> service.getMatch("EUW1_2")).isInstanceOf(MatchNotFoundException.class);
    }

    // --- 5.3 : stats par champion ---

    /** Données connues : 3 Kalista + 1 remake Kalista en SoloQ, 1 Annie en SoloQ, 1 Annie en Flex. */
    private void saveChampionGames() {
        save(game(1).queue(420).duration(1500).me(429, "Kalista", "BOTTOM", true, 6, 4, 3, 250, 19708),
                game(2).queue(420).duration(1800).me(429, "Kalista", "BOTTOM", false, 2, 5, 1, 200, 10000),
                game(3).queue(420).duration(1200).me(429, "Kalista", "BOTTOM", true, 10, 0, 5, 300, 30000),
                game(4).queue(420).duration(200).remake().me(429, "Kalista", "BOTTOM", false, 0, 9, 0, 5, 100),
                game(5).queue(420).duration(1800).me(1, "Annie", "MIDDLE", true, 5, 0, 7, 180, 25000),
                game(6).queue(440).duration(1800).me(1, "Annie", "MIDDLE", false, 3, 6, 2, 150, 12000));
    }

    @Test
    void calculeLesStatsParChampionSurDesDonneesConnues() {
        saveChampionGames();

        List<ChampionStatsResponse> stats = service.getChampionStats(RiotFixtures.PUUID, null);

        // Le plus joué d'abord ; le remake n'est pas compté
        assertThat(stats).extracting(ChampionStatsResponse::championName).containsExactly("Kalista", "Annie");
        ChampionStatsResponse kalista = stats.getFirst();
        assertThat(kalista.championId()).isEqualTo(429);
        assertThat(kalista.games()).isEqualTo(3);
        assertThat(kalista.wins()).isEqualTo(2);
        assertThat(kalista.losses()).isEqualTo(1);
        assertThat(kalista.winrate()).isCloseTo(66.667, within(0.001));
        // 18 / 9 / 9 en 3 parties
        assertThat(kalista.avgKills()).isEqualTo(6.0);
        assertThat(kalista.avgDeaths()).isEqualTo(3.0);
        assertThat(kalista.avgAssists()).isEqualTo(3.0);
        assertThat(kalista.kda()).isEqualTo(3.0);
        // 750 CS en 4500 s = 75 min
        assertThat(kalista.csPerMinute()).isEqualTo(10.0);
        assertThat(kalista.avgDamage()).isCloseTo(19902.667, within(0.001));

        ChampionStatsResponse annie = stats.get(1);
        assertThat(annie.games()).isEqualTo(2);
        assertThat(annie.winrate()).isEqualTo(50.0);
        assertThat(annie.kda()).isEqualTo((8 + 9) / 6.0);
        assertThat(annie.csPerMinute()).isEqualTo(330 / 60.0);
    }

    @Test
    void lesStatsSeFiltrentParFile() {
        saveChampionGames();

        assertThat(service.getChampionStats(RiotFixtures.PUUID, 440)).singleElement().satisfies(annie -> {
            assertThat(annie.championName()).isEqualTo("Annie");
            assertThat(annie.games()).isEqualTo(1);
            assertThat(annie.wins()).isZero();
        });
        assertThat(service.getChampionStats(RiotFixtures.PUUID, 420))
                .extracting(ChampionStatsResponse::championName, ChampionStatsResponse::games)
                .containsExactly(tuple("Kalista", 3L), tuple("Annie", 1L));
        assertThat(service.getChampionStats(RiotFixtures.PUUID, 450)).isEmpty();
    }

    @Test
    void sansAucuneMortLeKdaVautEliminationsPlusAssistances() {
        save(game(1).me(1, "Annie", "MIDDLE", true, 5, 0, 7, 180, 25000));

        assertThat(service.getChampionStats(RiotFixtures.PUUID, null).getFirst().kda()).isEqualTo(12.0);
    }

    @Test
    void lesStatsNeComptentQueLesPartiesDuJoueur() {
        save(game(1));

        // 10 joueurs dans la partie, mais un seul champion pour le joueur
        assertThat(service.getChampionStats(RiotFixtures.PUUID, null)).hasSize(1);
        assertThat(service.getChampionStats(RiotFixtures.PUUID, null).getFirst().games()).isEqualTo(1);
    }

    @Test
    void unJoueurSansPartieNAAucuneStat() {
        assertThat(service.getChampionStats(RiotFixtures.PUUID, null)).isEmpty();
    }

    @Test
    void lesStatsDUnPuuidInconnuDonnentJoueurIntrouvable() {
        assertThatThrownBy(() -> service.getChampionStats(RiotFixtures.puuid(9), null))
                .isInstanceOf(PlayerNotFoundException.class);
    }

    // --- 5.4 : résumé des 20 dernières parties ---

    @Test
    void leResumeCalculeWinrateKdaEtRolesHorsRemakes() {
        // Mêmes données que les stats par champion : 5 parties comptées, 1 remake ignoré
        saveChampionGames();

        PlayerSummaryResponse summary = service.getSummary(RiotFixtures.PUUID);

        assertThat(summary.games()).isEqualTo(5);
        assertThat(summary.wins()).isEqualTo(3);
        assertThat(summary.losses()).isEqualTo(2);
        assertThat(summary.winrate()).isEqualTo(60.0);
        // Éliminations 6+2+10+5+3 = 26, morts 4+5+0+0+6 = 15, assistances 3+1+5+7+2 = 18
        assertThat(summary.avgKills()).isEqualTo(26 / 5.0);
        assertThat(summary.avgDeaths()).isEqualTo(3.0);
        assertThat(summary.avgAssists()).isEqualTo(18 / 5.0);
        assertThat(summary.kda()).isEqualTo((26 + 18) / 15.0);
        assertThat(summary.roles()).containsExactly(
                new PlayerSummaryResponse.RoleResponse("BOTTOM", 3),
                new PlayerSummaryResponse.RoleResponse("MIDDLE", 2));
    }

    @Test
    void leResumeNePrendQueLesVingtDernieresParties() {
        // 22 parties : les 2 plus anciennes sont des défaites, les 20 plus récentes des victoires
        for (int n = 1; n <= 22; n++) {
            save(game(n).me(1, "Annie", "MIDDLE", n > 2, 1, 1, 1, 100, 1000));
        }

        PlayerSummaryResponse summary = service.getSummary(RiotFixtures.PUUID);

        assertThat(summary.games()).isEqualTo(MatchQueryService.SUMMARY_GAMES);
        assertThat(summary.winrate()).isEqualTo(100.0);
    }

    @Test
    void lesRolesAEgaliteSuiventLOrdreDeLaCarteEtLesPartiesSansRoleSontIgnorees() {
        save(game(1).me(1, "Annie", "UTILITY", true, 1, 1, 1, 10, 100),
                game(2).me(1, "Annie", "TOP", true, 1, 1, 1, 10, 100),
                game(3).queue(450).me(1, "Annie", "", true, 1, 1, 1, 10, 100));

        assertThat(service.getSummary(RiotFixtures.PUUID).roles())
                .extracting(PlayerSummaryResponse.RoleResponse::position).containsExactly("TOP", "UTILITY");
    }

    @Test
    void unJoueurSansPartieAUnResumeVide() {
        PlayerSummaryResponse summary = service.getSummary(RiotFixtures.PUUID);

        assertThat(summary.games()).isZero();
        assertThat(summary.winrate()).isZero();
        assertThat(summary.kda()).isZero();
        assertThat(summary.roles()).isEmpty();
    }

    @Test
    void leResumeDUnPuuidInconnuDonneJoueurIntrouvable() {
        assertThatThrownBy(() -> service.getSummary(RiotFixtures.puuid(9)))
                .isInstanceOf(PlayerNotFoundException.class);
    }

    @ParameterizedTest
    @CsvSource({"-1, 10", "0, 0", "0, 51"})
    void refuseUnePageOuUneTailleHorsLimites(int page, int size) {
        assertThatThrownBy(() -> service.getHistory(RiotFixtures.PUUID, page, size, null))
                .isInstanceOf(InvalidRequestException.class);
    }
}
