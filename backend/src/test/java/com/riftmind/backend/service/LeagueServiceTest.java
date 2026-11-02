package com.riftmind.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import com.riftmind.backend.CleanDatabase;
import com.riftmind.backend.TestcontainersConfiguration;
import com.riftmind.backend.model.LeagueEntry;
import com.riftmind.backend.model.Player;
import com.riftmind.backend.repository.LeagueEntryRepository;
import com.riftmind.backend.repository.PlayerRepository;
import com.riftmind.backend.riot.Platform;
import com.riftmind.backend.riot.RankedQueue;
import com.riftmind.backend.riot.RiotClient;
import com.riftmind.backend.riot.RiotFixtures;
import com.riftmind.backend.riot.RiotProperties;
import com.riftmind.backend.riot.RiotRateLimiter;
import com.riftmind.backend.riot.RiotUnavailableException;
import com.riftmind.backend.riot.dto.LeagueEntryDto;

/**
 * Rangs classés (tâche 3.2).
 *
 * <p>Riot est simulé par {@link MockRestServiceServer} avec les vraies réponses anonymisées ({@link RiotFixtures}).
 * La base est un vrai PostgreSQL, sans transaction de test : chaque écriture est validée comme en production, et
 * les tables sont vidées avant et après chaque test ({@link CleanDatabase}).
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanDatabase
@Import(TestcontainersConfiguration.class)
class LeagueServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-01T20:00:00Z");
    private static final String LEAGUE_URL =
            "https://euw1.api.riotgames.com/lol/league/v4/entries/by-puuid/" + RiotFixtures.PUUID;

    @Autowired
    private LeagueEntryRepository repository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private MockRestServiceServer riot;
    private RiotClient riotClient;
    private LeagueService service;
    private Player player;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        riot = MockRestServiceServer.bindTo(builder).build();
        RiotProperties properties = new RiotProperties("RGAPI-test-key-not-a-real-key",
                RiotProperties.RateLimit.defaults(), Duration.ofMinutes(2), Duration.ofSeconds(5),
                Duration.ofSeconds(10));
        riotClient = new RiotClient(builder, properties, new RiotRateLimiter(properties.rateLimit()));
        service = new LeagueService(riotClient, repository, transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));
        player = playerRepository.save(new Player(RiotFixtures.PUUID, RiotFixtures.GAME_NAME, RiotFixtures.TAG_LINE,
                Platform.EUW1, 512, 7, NOW.minus(Duration.ofDays(1))));
    }

    private void riotResponds(String json) {
        riot.expect(requestTo(LEAGUE_URL)).andExpect(header("X-Riot-Token", "RGAPI-test-key-not-a-real-key"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private static String entryJson(String queueType, String tier, String rank, int leaguePoints) {
        return """
                {"queueType": "%s", "tier": "%s", "rank": "%s", "leaguePoints": %d, "wins": 10, "losses": 5,
                 "hotStreak": true, "puuid": "%s"}
                """.formatted(queueType, tier, rank, leaguePoints, RiotFixtures.PUUID);
    }

    private void storeSoloAndFlex() {
        service.save(RiotFixtures.PUUID, List.of(
                new LeagueEntryDto("RANKED_FLEX_SR", "GOLD", "II", 45, 12, 10, false),
                new LeagueEntryDto("RANKED_SOLO_5x5", "MASTER", "I", 80, 100, 90, false)),
                NOW.minus(Duration.ofDays(1)));
    }

    @Test
    void recupereLesRangsChezRiotEtLesEnregistre() {
        riotResponds(RiotFixtures.read(RiotFixtures.LEAGUE_ENTRIES));

        List<LeagueEntry> entries = service.refresh(player);

        riot.verify();
        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.getQueueType()).isEqualTo(RankedQueue.RANKED_SOLO_5x5);
            assertThat(entry.getTier()).isEqualTo("CHALLENGER");
            assertThat(entry.getRank()).isEqualTo("I");
            assertThat(entry.getLeaguePoints()).isEqualTo(1234);
            assertThat(entry.getWins()).isEqualTo(250);
            assertThat(entry.getLosses()).isEqualTo(200);
            assertThat(entry.isHotStreak()).isFalse();
            assertThat(entry.getUpdatedAt()).isEqualTo(NOW);
        });
        assertThat(service.findByPuuid(RiotFixtures.PUUID)).containsExactlyElementsOf(entries);
    }

    @Test
    void unJoueurNonClasseNAAucunRang() {
        riotResponds(RiotFixtures.read(RiotFixtures.LEAGUE_ENTRIES_UNRANKED));

        assertThat(service.refresh(player)).isEmpty();
        assertThat(repository.count()).isZero();
    }

    @Test
    void appelleLHoteDeLaPlateformeDuJoueur() {
        Player korean = playerRepository.save(new Player(RiotFixtures.puuid(2), "Joueur 2", "KR1", Platform.KR, 30,
                1, NOW));
        riot.expect(requestTo("https://kr.api.riotgames.com/lol/league/v4/entries/by-puuid/" + RiotFixtures.puuid(2)))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        service.refresh(korean);

        riot.verify();
    }

    @Test
    void unRafraichissementMetAJourLesRangsSansDoublon() {
        storeSoloAndFlex();
        riotResponds("[" + entryJson("RANKED_SOLO_5x5", "GRANDMASTER", "I", 512) + ","
                + entryJson("RANKED_FLEX_SR", "PLATINUM", "IV", 0) + "]");

        service.refresh(player);

        assertThat(repository.count()).isEqualTo(2);
        assertThat(service.findByPuuid(RiotFixtures.PUUID))
                .extracting(LeagueEntry::getTier, LeagueEntry::getLeaguePoints, LeagueEntry::getUpdatedAt)
                .containsExactly(
                        tuple("GRANDMASTER", 512, NOW),
                        tuple("PLATINUM", 0, NOW));
    }

    @Test
    void uneFileAbsenteDeLaReponseEstSupprimee() {
        // Le joueur n'est plus classé en Flex (ex. après la fin de saison)
        storeSoloAndFlex();
        riotResponds("[" + entryJson("RANKED_SOLO_5x5", "MASTER", "I", 100) + "]");

        List<LeagueEntry> entries = service.refresh(player);

        assertThat(entries).extracting(LeagueEntry::getQueueType).containsExactly(RankedQueue.RANKED_SOLO_5x5);
        assertThat(repository.findByPuuid(RiotFixtures.PUUID)).hasSize(1);
    }

    @Test
    void lesFilesNonAfficheesSontIgnorees() {
        riotResponds("[" + entryJson("RANKED_FLEX_TT", "GOLD", "I", 10) + ","
                + entryJson("RANKED_SOLO_5x5", "SILVER", "III", 20) + ","
                + entryJson("CHERRY", "UNRANKED", "", 0) + "]");

        List<LeagueEntry> entries = service.refresh(player);

        assertThat(entries).extracting(LeagueEntry::getQueueType).containsExactly(RankedQueue.RANKED_SOLO_5x5);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void lesRangsSontTriesSoloQPuisFlex() {
        // Riot renvoie la Flex en premier, et l'ordre alphabétique la placerait aussi en premier
        storeSoloAndFlex();

        assertThat(service.findByPuuid(RiotFixtures.PUUID)).extracting(LeagueEntry::getQueueType)
                .containsExactly(RankedQueue.RANKED_SOLO_5x5, RankedQueue.RANKED_FLEX_SR);
    }

    @Test
    void unJoueurInconnuNAAucunRang() {
        assertThat(service.findByPuuid(RiotFixtures.puuid(9))).isEmpty();
    }

    @Test
    void uneErreurRiotNeModifiePasLesRangsEnBase() {
        storeSoloAndFlex();
        riot.expect(requestTo(LEAGUE_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> service.refresh(player)).isInstanceOf(RiotUnavailableException.class);
        assertThat(service.findByPuuid(RiotFixtures.PUUID)).extracting(LeagueEntry::getTier)
                .containsExactly("MASTER", "GOLD");
    }

    @Test
    void uneErreurEnBaseAnnuleToutesLesModifications() {
        storeSoloAndFlex();

        // La Flex est valide, mais la SoloQ viole la contrainte league_points >= 0 : rien ne doit être écrit
        assertThatThrownBy(() -> service.save(RiotFixtures.PUUID, List.of(
                new LeagueEntryDto("RANKED_FLEX_SR", "DIAMOND", "I", 99, 1, 1, false),
                new LeagueEntryDto("RANKED_SOLO_5x5", "MASTER", "I", -1, 1, 1, false)), NOW))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(service.findByPuuid(RiotFixtures.PUUID)).extracting(LeagueEntry::getTier)
                .containsExactly("MASTER", "GOLD");
    }

    /**
     * Deux mises à jour simultanées d'un joueur sans rang : la seconde échoue à l'INSERT (clé déjà présente) et
     * renvoie les rangs enregistrés par la première. Impossible à provoquer de façon fiable avec une vraie base,
     * d'où un repository simulé.
     */
    @Test
    void deuxMisesAJourSimultaneesNeProvoquentPasDErreur() {
        LeagueEntry savedByOtherRequest = new LeagueEntry(RiotFixtures.PUUID, RankedQueue.RANKED_SOLO_5x5,
                "CHALLENGER", "I", 1234, 250, 200, false, NOW);
        LeagueEntryRepository racingRepository = mock(LeagueEntryRepository.class);
        when(racingRepository.findByPuuid(RiotFixtures.PUUID))
                .thenReturn(List.of())                     // rien au moment de la lecture…
                .thenReturn(List.of(savedByOtherRequest)); // …mais présent juste après l'échec de l'INSERT
        when(racingRepository.saveAll(anyIterable()))
                .thenThrow(new DuplicateKeyException("duplicate key value violates league_entry_pkey"));
        riotResponds(RiotFixtures.read(RiotFixtures.LEAGUE_ENTRIES));

        List<LeagueEntry> entries = new LeagueService(riotClient, racingRepository, transactionManager,
                Clock.fixed(NOW, ZoneOffset.UTC)).refresh(player);

        assertThat(entries).containsExactly(savedByOtherRequest);
    }
}
