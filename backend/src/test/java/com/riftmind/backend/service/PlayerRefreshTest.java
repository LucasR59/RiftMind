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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import com.riftmind.backend.exception.PlayerNotFoundException;
import com.riftmind.backend.exception.RefreshCooldownException;
import com.riftmind.backend.model.LeagueEntry;
import com.riftmind.backend.model.Player;
import com.riftmind.backend.repository.LeagueEntryRepository;
import com.riftmind.backend.repository.MatchParticipantRepository;
import com.riftmind.backend.repository.MatchRepository;
import com.riftmind.backend.repository.PlayerRepository;
import com.riftmind.backend.riot.Platform;
import com.riftmind.backend.riot.RiotClient;
import com.riftmind.backend.riot.RiotFixtures;
import com.riftmind.backend.riot.RiotProperties;
import com.riftmind.backend.riot.RiotRateLimitException;
import com.riftmind.backend.riot.RiotRateLimiter;
import com.riftmind.backend.riot.RiotUnavailableException;

import tools.jackson.databind.json.JsonMapper;

/**
 * Mise à jour d'un joueur depuis Riot, avec un délai minimum de 2 min entre deux mises à jour (tâche 5.5).
 *
 * <p>Riot est simulé par {@link MockRestServiceServer} avec les vraies réponses anonymisées ; tous les services sont
 * réels, sur un vrai PostgreSQL, sans transaction de test, vidé avant et après chaque test ({@link CleanDatabase}).
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanDatabase
@Import(TestcontainersConfiguration.class)
class PlayerRefreshTest {

    private static final Duration COOLDOWN = Duration.ofMinutes(2);
    /** Dernière mise à jour du joueur. */
    private static final Instant T0 = Instant.parse("2026-11-02T10:00:00Z");
    /** Heure de la demande de mise à jour : 5 min après T0, le délai est écoulé. */
    private static final Instant NOW = T0.plus(Duration.ofMinutes(5));
    private static final String EUROPE = "https://europe.api.riotgames.com";
    private static final String EUW1 = "https://euw1.api.riotgames.com";
    private static final String SUMMONER_URL = EUW1 + "/lol/summoner/v4/summoners/by-puuid/" + RiotFixtures.PUUID;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private LeagueEntryRepository leagueEntryRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private MatchParticipantRepository participantRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private MockRestServiceServer riot;
    private RiotClient riotClient;
    private RiotProperties properties;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        riot = MockRestServiceServer.bindTo(builder).build();
        properties = new RiotProperties("RGAPI-test-key-not-a-real-key", RiotProperties.RateLimit.defaults(),
                COOLDOWN, Duration.ofSeconds(5), Duration.ofSeconds(10));
        riotClient = new RiotClient(builder, properties, new RiotRateLimiter(properties.rateLimit()));
        // Le joueur s'appelait autrement à sa dernière mise à jour
        playerRepository.save(new Player(RiotFixtures.PUUID, "Ancien Nom", "OLD", Platform.EUW1, 100, 1, T0));
    }

    private PlayerService serviceAt(Instant instant) {
        Clock clock = Clock.fixed(instant, ZoneOffset.UTC);
        LeagueService leagueService = new LeagueService(riotClient, leagueEntryRepository, transactionManager, clock);
        MatchIngestionService matchIngestionService = new MatchIngestionService(riotClient, matchRepository,
                participantRepository, transactionManager, JsonMapper.builder().build(), clock);
        return new PlayerService(riotClient, playerRepository, leagueService, matchIngestionService, properties,
                clock);
    }

    private void riotRespondsWithProfile() {
        riot.expect(requestTo(EUROPE + "/riot/account/v1/accounts/by-puuid/" + RiotFixtures.PUUID))
                .andRespond(withSuccess(RiotFixtures.read(RiotFixtures.ACCOUNT), MediaType.APPLICATION_JSON));
        riot.expect(requestTo(SUMMONER_URL))
                .andRespond(withSuccess(RiotFixtures.read(RiotFixtures.SUMMONER), MediaType.APPLICATION_JSON));
        riot.expect(requestTo(EUW1 + "/lol/league/v4/entries/by-puuid/" + RiotFixtures.PUUID))
                .andRespond(withSuccess(RiotFixtures.read(RiotFixtures.LEAGUE_ENTRIES), MediaType.APPLICATION_JSON));
    }

    private void riotListsMatch() {
        riot.expect(requestTo(EUROPE + "/lol/match/v5/matches/by-puuid/" + RiotFixtures.PUUID
                + "/ids?start=0&count=20"))
                .andRespond(withSuccess("[\"" + RiotFixtures.MATCH_ID + "\"]", MediaType.APPLICATION_JSON));
    }

    private void riotRespondsWithEverything() {
        riotRespondsWithProfile();
        riotListsMatch();
        riot.expect(requestTo(EUROPE + "/lol/match/v5/matches/" + RiotFixtures.MATCH_ID))
                .andRespond(withSuccess(RiotFixtures.read(RiotFixtures.MATCH), MediaType.APPLICATION_JSON));
    }

    private Instant lastRefreshedAt() {
        return playerRepository.findById(RiotFixtures.PUUID).orElseThrow().getLastRefreshedAt();
    }

    @Test
    void metAJourLeJoueurSesRangsEtSesParties() {
        riotRespondsWithEverything();

        Player player = serviceAt(NOW).refresh(RiotFixtures.PUUID);

        riot.verify();
        // Nouveau Riot ID, niveau et icône
        assertThat(player.getRiotId()).isEqualTo("Joueur Test#EUW");
        assertThat(player.getSummonerLevel()).isEqualTo(512);
        assertThat(player.getProfileIconId()).isEqualTo(7);
        assertThat(player.getLastRefreshedAt()).isEqualTo(NOW);
        assertThat(lastRefreshedAt()).isEqualTo(NOW);
        assertThat(player.getCreatedAt()).isEqualTo(T0);
        assertThat(leagueEntryRepository.findByPuuid(RiotFixtures.PUUID)).extracting(LeagueEntry::getTier)
                .containsExactly("CHALLENGER");
        assertThat(matchRepository.existsById(RiotFixtures.MATCH_ID)).isTrue();
    }

    @Test
    void uneMiseAJourTropRecenteEstRefuseeSansAppelerRiot() {
        // 30 s après la dernière mise à jour : encore 90 s à attendre
        assertThatThrownBy(() -> serviceAt(T0.plusSeconds(30)).refresh(RiotFixtures.PUUID))
                .isInstanceOfSatisfying(RefreshCooldownException.class,
                        e -> assertThat(e.retryAfterSeconds()).isEqualTo(90));

        riot.verify();
        assertThat(lastRefreshedAt()).isEqualTo(T0);
    }

    @Test
    void uneMiseAJourEstPossibleDesLaFinDuDelai() {
        riotRespondsWithEverything();

        serviceAt(T0.plus(COOLDOWN)).refresh(RiotFixtures.PUUID);

        riot.verify();
        assertThat(lastRefreshedAt()).isEqualTo(T0.plus(COOLDOWN));
    }

    @Test
    void justeApresUneMiseAJourIlFautAttendreToutLeDelai() {
        riotRespondsWithEverything();
        serviceAt(NOW).refresh(RiotFixtures.PUUID);
        riot.reset();

        assertThatThrownBy(() -> serviceAt(NOW.plusSeconds(1)).refresh(RiotFixtures.PUUID))
                .isInstanceOfSatisfying(RefreshCooldownException.class,
                        e -> assertThat(e.retryAfterSeconds()).isEqualTo(119));
        riot.verify();
    }

    @Test
    void deuxMisesAJourSimultaneesNeSontPasToutesLesDeuxAcceptees() {
        // La réservation est une seule requête SQL conditionnelle : la seconde trouve le délai déjà réservé
        assertThat(playerRepository.claimRefresh(RiotFixtures.PUUID, NOW, NOW.minus(COOLDOWN))).isEqualTo(1);
        assertThat(playerRepository.claimRefresh(RiotFixtures.PUUID, NOW, NOW.minus(COOLDOWN))).isZero();
    }

    @Test
    void siRiotEchoueLeDelaiEstRenduEtOnPeutReessayer() {
        riot.expect(requestTo(EUROPE + "/riot/account/v1/accounts/by-puuid/" + RiotFixtures.PUUID))
                .andRespond(withSuccess(RiotFixtures.read(RiotFixtures.ACCOUNT), MediaType.APPLICATION_JSON));
        riot.expect(requestTo(SUMMONER_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> serviceAt(NOW).refresh(RiotFixtures.PUUID))
                .isInstanceOf(RiotUnavailableException.class);
        assertThat(lastRefreshedAt()).as("délai rendu").isEqualTo(T0);
        assertThat(playerRepository.findById(RiotFixtures.PUUID).orElseThrow().getRiotId())
                .as("rien n'a été modifié").isEqualTo("Ancien Nom#OLD");

        riot.reset();
        riotRespondsWithEverything();
        serviceAt(NOW.plusSeconds(1)).refresh(RiotFixtures.PUUID);
        riot.verify();
    }

    @Test
    void siLaRecuperationDesPartiesEchoueLeProfilEstGardeEtLeDelaiRendu() {
        riotRespondsWithProfile();
        riotListsMatch();
        riot.expect(requestTo(EUROPE + "/lol/match/v5/matches/" + RiotFixtures.MATCH_ID))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "0"));

        assertThatThrownBy(() -> serviceAt(NOW).refresh(RiotFixtures.PUUID))
                .isInstanceOf(RiotRateLimitException.class);

        Player player = playerRepository.findById(RiotFixtures.PUUID).orElseThrow();
        assertThat(player.getRiotId()).isEqualTo("Joueur Test#EUW");
        assertThat(leagueEntryRepository.findByPuuid(RiotFixtures.PUUID)).hasSize(1);
        assertThat(player.getLastRefreshedAt()).as("délai rendu : le joueur peut relancer").isEqualTo(T0);
    }

    @Test
    void unPuuidInconnuDonneJoueurIntrouvable() {
        assertThatThrownBy(() -> serviceAt(NOW).refresh(RiotFixtures.puuid(9)))
                .isInstanceOf(PlayerNotFoundException.class);
        riot.verify();
    }
}
