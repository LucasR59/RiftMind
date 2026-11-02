package com.riftmind.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import com.riftmind.backend.CleanDatabase;
import com.riftmind.backend.TestcontainersConfiguration;
import com.riftmind.backend.model.Match;
import com.riftmind.backend.model.MatchParticipant;
import com.riftmind.backend.model.Player;
import com.riftmind.backend.repository.MatchParticipantRepository;
import com.riftmind.backend.repository.MatchRepository;
import com.riftmind.backend.riot.Platform;
import com.riftmind.backend.riot.RiotClient;
import com.riftmind.backend.riot.RiotFixtures;
import com.riftmind.backend.riot.RiotProperties;
import com.riftmind.backend.riot.RiotRateLimitException;
import com.riftmind.backend.riot.RiotRateLimiter;
import com.riftmind.backend.riot.RiotUnavailableException;

import tools.jackson.databind.json.JsonMapper;

/**
 * Récupération des parties d'un joueur (tâche 4.4).
 *
 * <p>Riot est simulé par {@link MockRestServiceServer} : toute requête inattendue fait échouer le test, ce qui
 * vérifie qu'une partie déjà en base n'est jamais redemandée. Les parties sont des copies de {@code match.json} avec un
 * autre identifiant. La base est un vrai PostgreSQL, sans transaction de test, vidée avant et après chaque test
 * ({@link CleanDatabase}).
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanDatabase
@Import(TestcontainersConfiguration.class)
class MatchIngestionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private static final String EUROPE = "https://europe.api.riotgames.com";
    private static final String IDS_URL = EUROPE + "/lol/match/v5/matches/by-puuid/" + RiotFixtures.PUUID
            + "/ids?start=0&count=20";

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private MatchParticipantRepository participantRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private MockRestServiceServer riot;
    private MatchIngestionService service;
    private Player player;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        riot = MockRestServiceServer.bindTo(builder).build();
        RiotProperties properties = new RiotProperties("RGAPI-test-key-not-a-real-key",
                RiotProperties.RateLimit.defaults(), Duration.ofMinutes(2), Duration.ofSeconds(5),
                Duration.ofSeconds(10));
        RiotClient riotClient = new RiotClient(builder, properties, new RiotRateLimiter(properties.rateLimit()));
        service = new MatchIngestionService(riotClient, matchRepository, participantRepository, transactionManager,
                JsonMapper.builder().build(), Clock.fixed(NOW, ZoneOffset.UTC));
        player = new Player(RiotFixtures.PUUID, RiotFixtures.GAME_NAME, RiotFixtures.TAG_LINE, Platform.EUW1, 512, 7,
                NOW);
    }

    private static String matchId(int n) {
        return "EUW1_70000000%02d".formatted(n);
    }

    /** {@code match.json} avec un autre identifiant. */
    private static String matchJson(String matchId) {
        return RiotFixtures.read(RiotFixtures.MATCH).replace(RiotFixtures.MATCH_ID, matchId);
    }

    private static String idsJson(List<String> ids) {
        return ids.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(",", "[", "]"));
    }

    private void riotListsMatches(String url, List<String> ids) {
        riot.expect(requestTo(url)).andRespond(withSuccess(idsJson(ids), MediaType.APPLICATION_JSON));
    }

    private void riotReturnsMatch(String host, String matchId) {
        riot.expect(requestTo(host + "/lol/match/v5/matches/" + matchId))
                .andRespond(withSuccess(matchJson(matchId), MediaType.APPLICATION_JSON));
    }

    @Test
    void recupereEtEnregistreLesDernieresParties() {
        List<String> ids = List.of(matchId(1), matchId(2), matchId(3));
        riotListsMatches(IDS_URL, ids);
        ids.forEach(id -> riotReturnsMatch(EUROPE, id));

        MatchIngestionService.SyncResult result = service.syncRecent(player);

        riot.verify();
        assertThat(result).isEqualTo(new MatchIngestionService.SyncResult(3, 0, 3));
        assertThat(matchRepository.findAll()).extracting(Match::getMatchId).containsExactlyInAnyOrderElementsOf(ids);
        assertThat(participantRepository.count()).isEqualTo(30);
        Match saved = matchRepository.findById(matchId(2)).orElseThrow();
        assertThat(saved.getPlatform()).isEqualTo("EUW1");
        assertThat(saved.getQueueId()).isEqualTo(420);
        assertThat(saved.getDurationSeconds()).isEqualTo(1463);
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getRaw()).contains(matchId(2));
        assertThat(participantRepository.findByMatchIdOrderByParticipantId(matchId(2)))
                .filteredOn(p -> p.getPuuid().equals(RiotFixtures.PUUID))
                .singleElement().extracting(MatchParticipant::getChampionName).isEqualTo("Kalista");
    }

    /** Critère de fin de la tâche 4.4. */
    @Test
    void unSecondAppelNeRedemandeAucunePartieARiot() {
        List<String> ids = List.of(matchId(1), matchId(2));
        riotListsMatches(IDS_URL, ids);
        ids.forEach(id -> riotReturnsMatch(EUROPE, id));
        service.syncRecent(player);
        riot.verify();
        riot.reset();

        // Seule la liste des identifiants est redemandée : c'est elle qui révèle les nouvelles parties
        riotListsMatches(IDS_URL, ids);
        MatchIngestionService.SyncResult result = service.syncRecent(player);

        riot.verify();
        assertThat(result).isEqualTo(new MatchIngestionService.SyncResult(2, 2, 0));
        assertThat(matchRepository.count()).isEqualTo(2);
        assertThat(participantRepository.count()).isEqualTo(20);
    }

    @Test
    void neDemandeQueLesNouvellesParties() {
        riotListsMatches(IDS_URL, List.of(matchId(1)));
        riotReturnsMatch(EUROPE, matchId(1));
        service.syncRecent(player);
        riot.reset();

        // Deux parties jouées depuis
        riotListsMatches(IDS_URL, List.of(matchId(3), matchId(2), matchId(1)));
        riotReturnsMatch(EUROPE, matchId(3));
        riotReturnsMatch(EUROPE, matchId(2));
        MatchIngestionService.SyncResult result = service.syncRecent(player);

        riot.verify();
        assertThat(result).isEqualTo(new MatchIngestionService.SyncResult(3, 1, 2));
        assertThat(matchRepository.count()).isEqualTo(3);
    }

    @Test
    void appelleLaRegionDeRoutageDeLaPlateformeDuJoueur() {
        String asia = "https://asia.api.riotgames.com";
        riotListsMatches(asia + "/lol/match/v5/matches/by-puuid/" + RiotFixtures.PUUID + "/ids?start=0&count=5",
                List.of("KR_1"));
        riot.expect(requestTo(asia + "/lol/match/v5/matches/KR_1"))
                .andRespond(withSuccess(matchJson("KR_1"), MediaType.APPLICATION_JSON));

        service.syncRecent(Platform.KR, RiotFixtures.PUUID, 5);

        riot.verify();
    }

    @Test
    void unJoueurSansPartieNeDeclencheQuUnAppel() {
        riotListsMatches(IDS_URL, List.of());

        assertThat(service.syncRecent(player)).isEqualTo(new MatchIngestionService.SyncResult(0, 0, 0));
        riot.verify();
    }

    /** Tâche 4.5 : un remake est enregistré (il reste dans l'historique), marqué pour être exclu des statistiques. */
    @Test
    void unRemakeEstEnregistreEtMarque() {
        riotListsMatches(IDS_URL, List.of(matchId(1), matchId(2)));
        riotReturnsMatch(EUROPE, matchId(1));
        riot.expect(requestTo(EUROPE + "/lol/match/v5/matches/" + matchId(2))).andRespond(withSuccess(
                RiotFixtures.remakeMatch().replace(RiotFixtures.MATCH_ID, matchId(2)), MediaType.APPLICATION_JSON));

        service.syncRecent(player);

        assertThat(matchRepository.findById(matchId(1)).orElseThrow().isRemake()).isFalse();
        Match remake = matchRepository.findById(matchId(2)).orElseThrow();
        assertThat(remake.isRemake()).isTrue();
        assertThat(remake.getDurationSeconds()).isEqualTo(210);
        assertThat(participantRepository.findByMatchIdOrderByParticipantId(matchId(2))).hasSize(10);
    }

    /** Tâche 4.6 : Riot interdit d'afficher l'historique des parties personnalisées. */
    @Test
    void unePartiePersonnaliseeEstIgnoreeEtNEstPasRedemandee() {
        String custom = matchJson(matchId(2)).replace("\"queueId\": 420", "\"queueId\": 0")
                .replace("\"gameType\": \"MATCHED_GAME\"", "\"gameType\": \"CUSTOM_GAME\"");
        riotListsMatches(IDS_URL, List.of(matchId(1), matchId(2)));
        riotReturnsMatch(EUROPE, matchId(1));
        riot.expect(requestTo(EUROPE + "/lol/match/v5/matches/" + matchId(2)))
                .andRespond(withSuccess(custom, MediaType.APPLICATION_JSON));

        service.syncRecent(player);

        // Ni joueurs (donc absente de l'historique et des stats), ni réponse brute : seul un marqueur reste
        assertThat(participantRepository.findByMatchIdOrderByParticipantId(matchId(2))).isEmpty();
        Match marker = matchRepository.findById(matchId(2)).orElseThrow();
        assertThat(marker.getQueueId()).isZero();
        assertThat(marker.getRaw()).isNull();
        assertThat(participantRepository.findByMatchIdOrderByParticipantId(matchId(1))).hasSize(10);

        // Le marqueur évite de la redemander à Riot à chaque synchronisation
        riot.reset();
        riotListsMatches(IDS_URL, List.of(matchId(1), matchId(2)));
        assertThat(service.syncRecent(player).alreadyKnown()).isEqualTo(2);
        riot.verify();
    }

    @Test
    void unePartieIntrouvableChezRiotEstIgnoree() {
        riotListsMatches(IDS_URL, List.of(matchId(1), matchId(2), matchId(3)));
        riotReturnsMatch(EUROPE, matchId(1));
        riot.expect(requestTo(EUROPE + "/lol/match/v5/matches/" + matchId(2)))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        riotReturnsMatch(EUROPE, matchId(3));

        MatchIngestionService.SyncResult result = service.syncRecent(player);

        riot.verify();
        assertThat(result.saved()).isEqualTo(2);
        assertThat(matchRepository.findExistingIds(List.of(matchId(1), matchId(2), matchId(3))))
                .containsExactlyInAnyOrder(matchId(1), matchId(3));
    }

    @Test
    void unePartieIllisibleEstIgnoree() {
        riotListsMatches(IDS_URL, List.of(matchId(1), matchId(2)));
        riot.expect(requestTo(EUROPE + "/lol/match/v5/matches/" + matchId(1)))
                .andRespond(withSuccess("{\"metadata\": {}}", MediaType.APPLICATION_JSON));
        riotReturnsMatch(EUROPE, matchId(2));

        assertThat(service.syncRecent(player).saved()).isEqualTo(1);
        assertThat(matchRepository.existsById(matchId(1))).isFalse();
    }

    @Test
    void siRiotEchoueEnCoursDeRouteLesPartiesDejaRecupereesSontEnregistrees() {
        riotListsMatches(IDS_URL, List.of(matchId(1), matchId(2), matchId(3)));
        riotReturnsMatch(EUROPE, matchId(1));
        riot.expect(requestTo(EUROPE + "/lol/match/v5/matches/" + matchId(2)))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "0"));

        assertThatThrownBy(() -> service.syncRecent(player)).isInstanceOf(RiotRateLimitException.class);

        riot.verify();
        assertThat(matchRepository.findAll()).extracting(Match::getMatchId).containsExactly(matchId(1));
        assertThat(participantRepository.count()).isEqualTo(10);
    }

    @Test
    void siLaListeEstIndisponibleRienNEstEnregistre() {
        riot.expect(requestTo(IDS_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> service.syncRecent(player)).isInstanceOf(RiotUnavailableException.class);
        assertThat(matchRepository.count()).isZero();
    }

    /**
     * Pendant qu'on récupère la partie 2, une autre requête (ex. la synchronisation d'un coéquipier) enregistre la
     * partie 1 : l'INSERT de la partie 1 échoue sur une vraie clé dupliquée, et l'écriture est refaite sans elle.
     */
    @Test
    void unePartieEnregistreeEntreTempsParUneAutreRequeteNeProvoquePasDErreur() {
        riotListsMatches(IDS_URL, List.of(matchId(1), matchId(2)));
        riotReturnsMatch(EUROPE, matchId(1));
        riot.expect(requestTo(EUROPE + "/lol/match/v5/matches/" + matchId(2))).andRespond(request -> {
            matchRepository.save(new Match(matchId(1), "EUW1", 420, "16.21", NOW, 1463, false, null, NOW));
            return withSuccess(matchJson(matchId(2)), MediaType.APPLICATION_JSON).createResponse(request);
        });

        MatchIngestionService.SyncResult result = service.syncRecent(player);

        riot.verify();
        assertThat(result.saved()).as("seule la partie 2 a été écrite par cet appel").isEqualTo(1);
        assertThat(matchRepository.count()).isEqualTo(2);
        assertThat(participantRepository.findByMatchIdOrderByParticipantId(matchId(1)))
                .as("la partie 1 est celle de l'autre requête").isEmpty();
        assertThat(participantRepository.findByMatchIdOrderByParticipantId(matchId(2))).hasSize(10);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101})
    void refuseUnNombreDePartiesHorsLimites(int count) {
        assertThatThrownBy(() -> service.syncRecent(Platform.EUW1, RiotFixtures.PUUID, count))
                .isInstanceOf(IllegalArgumentException.class);
        riot.verify();
    }
}
