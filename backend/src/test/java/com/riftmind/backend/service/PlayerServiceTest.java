package com.riftmind.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import com.riftmind.backend.CleanDatabase;
import com.riftmind.backend.TestcontainersConfiguration;
import com.riftmind.backend.exception.InvalidRequestException;
import com.riftmind.backend.exception.PlayerNotFoundException;
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
 * Recherche d'un joueur (tâche 2.4), enregistré avec ses rangs (tâche 3.3).
 *
 * <p>Riot est simulé par {@link MockRestServiceServer} avec les vraies réponses anonymisées
 * ({@link RiotFixtures}) : les URL appelées sont vérifiées, et toute requête inattendue fait échouer le test.
 * La base est un vrai PostgreSQL, sans transaction de test : chaque accès au repository est validé, comme en
 * production, et les tables sont vidées avant et après chaque test ({@link CleanDatabase}).
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanDatabase
@Import(TestcontainersConfiguration.class)
class PlayerServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-01T20:00:00Z");
    private static final String EUROPE = "https://europe.api.riotgames.com";
    private static final String EUW1 = "https://euw1.api.riotgames.com";
    private static final String ACCOUNT_URL = EUROPE + "/riot/account/v1/accounts/by-riot-id/Joueur%20Test/EUW";
    private static final String SUMMONER_URL = EUW1 + "/lol/summoner/v4/summoners/by-puuid/" + RiotFixtures.PUUID;
    private static final String LEAGUE_URL = EUW1 + "/lol/league/v4/entries/by-puuid/" + RiotFixtures.PUUID;

    @Autowired
    private PlayerRepository repository;

    @Autowired
    private LeagueEntryRepository leagueEntryRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private MockRestServiceServer riot;
    private RiotClient riotClient;
    private LeagueService leagueService;
    private PlayerService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        riot = MockRestServiceServer.bindTo(builder).build();
        RiotProperties properties = new RiotProperties("RGAPI-test-key-not-a-real-key",
                RiotProperties.RateLimit.defaults(), Duration.ofMinutes(2), Duration.ofSeconds(5),
                Duration.ofSeconds(10));
        riotClient = new RiotClient(builder, properties, new RiotRateLimiter(properties.rateLimit()));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        leagueService = new LeagueService(riotClient, leagueEntryRepository, transactionManager, clock);
        service = new PlayerService(riotClient, repository, leagueService, clock);
    }

    private void riotRespondsWithAccountAndSummoner(String accountUrl, String summonerUrl) {
        riot.expect(requestTo(accountUrl)).andExpect(header("X-Riot-Token", "RGAPI-test-key-not-a-real-key"))
                .andRespond(withSuccess(RiotFixtures.read(RiotFixtures.ACCOUNT), MediaType.APPLICATION_JSON));
        riot.expect(requestTo(summonerUrl))
                .andRespond(withSuccess(RiotFixtures.read(RiotFixtures.SUMMONER), MediaType.APPLICATION_JSON));
    }

    /** Les trois appels d'une première recherche : compte, invocateur, puis rangs (même hôte que l'invocateur). */
    private void riotRespondsWithPlayer(String accountUrl, String summonerUrl, String leagueFixture) {
        riotRespondsWithAccountAndSummoner(accountUrl, summonerUrl);
        riot.expect(requestTo(summonerUrl.replace("/lol/summoner/v4/summoners/", "/lol/league/v4/entries/")))
                .andRespond(withSuccess(RiotFixtures.read(leagueFixture), MediaType.APPLICATION_JSON));
    }

    private void riotRespondsWithPlayer(String accountUrl, String summonerUrl) {
        riotRespondsWithPlayer(accountUrl, summonerUrl, RiotFixtures.LEAGUE_ENTRIES);
    }

    @Test
    void unNouveauJoueurEstRecupereChezRiotPuisEnregistre() {
        riotRespondsWithPlayer(ACCOUNT_URL, SUMMONER_URL);

        Player player = service.getOrFetch(Platform.EUW1, "Joueur Test", "EUW");

        riot.verify();
        assertThat(player.getPuuid()).isEqualTo(RiotFixtures.PUUID);
        assertThat(player.getRiotId()).isEqualTo("Joueur Test#EUW");
        assertThat(player.getPlatform()).isEqualTo(Platform.EUW1);
        assertThat(player.getSummonerLevel()).isEqualTo(512);
        assertThat(player.getProfileIconId()).isEqualTo(7);
        assertThat(player.getLastRefreshedAt()).isEqualTo(NOW);
        assertThat(repository.findById(RiotFixtures.PUUID)).isPresent();
    }

    @Test
    void unNouveauJoueurEstEnregistreAvecSesRangs() {
        riotRespondsWithPlayer(ACCOUNT_URL, SUMMONER_URL);

        service.getOrFetch(Platform.EUW1, "Joueur Test", "EUW");

        riot.verify();
        assertThat(leagueService.findByPuuid(RiotFixtures.PUUID)).singleElement().satisfies(entry -> {
            assertThat(entry.getQueueType()).isEqualTo(RankedQueue.RANKED_SOLO_5x5);
            assertThat(entry.getTier()).isEqualTo("CHALLENGER");
            assertThat(entry.getLeaguePoints()).isEqualTo(1234);
            assertThat(entry.getUpdatedAt()).as("même instant que le joueur").isEqualTo(NOW);
        });
    }

    @Test
    void unJoueurNonClasseEstEnregistreSansRang() {
        riotRespondsWithPlayer(ACCOUNT_URL, SUMMONER_URL, RiotFixtures.LEAGUE_ENTRIES_UNRANKED);

        Player player = service.getOrFetch(Platform.EUW1, "Joueur Test", "EUW");

        riot.verify();
        assertThat(player.getPuuid()).isEqualTo(RiotFixtures.PUUID);
        assertThat(leagueService.findByPuuid(RiotFixtures.PUUID)).isEmpty();
    }

    @Test
    void uneErreurRiotSurLesRangsNEnregistreRienEtLaRechercheSuivanteReessaie() {
        riotRespondsWithAccountAndSummoner(ACCOUNT_URL, SUMMONER_URL);
        riot.expect(requestTo(LEAGUE_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> service.getOrFetch(Platform.EUW1, "Joueur Test", "EUW"))
                .isInstanceOf(RiotUnavailableException.class);
        riot.verify();
        // Sans joueur en base, la prochaine recherche rappellera Riot au lieu de renvoyer un joueur sans rangs
        assertThat(repository.count()).isZero();
        assertThat(leagueEntryRepository.count()).isZero();
    }

    @Test
    void unJoueurDejaEnBaseGardeSesRangsSansAppelerRiot() {
        repository.save(new Player(RiotFixtures.PUUID, "Joueur Test", "EUW", Platform.EUW1, 100, 1, NOW));
        leagueService.save(RiotFixtures.PUUID, List.of(
                new LeagueEntryDto("RANKED_FLEX_SR", "GOLD", "II", 45, 12, 10, false)),
                NOW);

        service.getOrFetch(Platform.EUW1, "Joueur Test", "EUW");

        riot.verify();
        assertThat(leagueService.findByPuuid(RiotFixtures.PUUID)).extracting(LeagueEntry::getTier)
                .containsExactly("GOLD");
    }

    /** Vu en direct : la 1ʳᵉ réponse avait des nanosecondes, la suivante (relue en base) des microsecondes. */
    @Test
    void laDateDeMiseAJourRenvoyeeEstCelleStockeeEnBase() {
        Instant withNanos = Instant.parse("2026-11-01T23:22:34.164986960Z");
        service = new PlayerService(riotClient, repository, leagueService, Clock.fixed(withNanos, ZoneOffset.UTC));
        riotRespondsWithPlayer(ACCOUNT_URL, SUMMONER_URL);

        Player player = service.getOrFetch(Platform.EUW1, "Joueur Test", "EUW");

        assertThat(player.getLastRefreshedAt()).isEqualTo(Instant.parse("2026-11-01T23:22:34.164986Z"))
                .isEqualTo(repository.findById(RiotFixtures.PUUID).orElseThrow().getLastRefreshedAt());
    }

    @Test
    void unJoueurDejaEnBaseNAppellePasRiot() {
        repository.save(new Player(RiotFixtures.PUUID, "Joueur Test", "EUW", Platform.EUW1, 100, 1,
                NOW.minus(Duration.ofDays(3))));

        // Aucune requête attendue : un appel à Riot ferait échouer le test
        Player player = service.getOrFetch(Platform.EUW1, "joueur test", "euw");

        riot.verify();
        assertThat(player.getSummonerLevel()).as("données en base, non rafraîchies").isEqualTo(100);
    }

    @Test
    void leJoueurGardeLEcritureExacteDeRiotEtLaSaisieEstNettoyee() {
        riotRespondsWithPlayer(
                EUROPE + "/riot/account/v1/accounts/by-riot-id/joueur%20test/euw", SUMMONER_URL);

        Player player = service.getOrFetch(Platform.EUW1, "  joueur test ", " euw");

        assertThat(player.getRiotId()).isEqualTo("Joueur Test#EUW");
        // La recherche suivante, avec une autre casse, trouve le joueur en base sans appeler Riot
        assertThat(service.getOrFetch(Platform.EUW1, "JOUEUR TEST", "EUW")).isEqualTo(player);
        riot.verify();
    }

    @Test
    void unJoueurQuiAChangeDeNomEstMisAJourSansDoublon() {
        // Le PUUID est connu sous son ancien Riot ID, sur une autre plateforme
        repository.save(new Player(RiotFixtures.PUUID, "Ancien Nom", "OLD", Platform.EUN1, 100, 1,
                NOW.minus(Duration.ofDays(30))));
        riotRespondsWithPlayer(ACCOUNT_URL, SUMMONER_URL);

        Player player = service.getOrFetch(Platform.EUW1, "Joueur Test", "EUW");

        assertThat(repository.count()).isEqualTo(1);
        assertThat(player.getRiotId()).isEqualTo("Joueur Test#EUW");
        assertThat(player.getPlatform()).isEqualTo(Platform.EUW1);
        assertThat(player.getSummonerLevel()).isEqualTo(512);
        assertThat(player.getCreatedAt()).isEqualTo(NOW.minus(Duration.ofDays(30)));
    }

    @Test
    void unRiotIdInconnuDonneJoueurIntrouvableSansRienEnregistrer() {
        riot.expect(requestTo(ACCOUNT_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON).body(RiotFixtures.read(RiotFixtures.ACCOUNT_NOT_FOUND)));

        assertThatThrownBy(() -> service.getOrFetch(Platform.EUW1, "Joueur Test", "EUW"))
                .isInstanceOf(PlayerNotFoundException.class)
                .hasMessage("Aucun joueur « Joueur Test#EUW » sur EUW (Europe Ouest).");
        riot.verify();
        assertThat(repository.count()).isZero();
    }

    @Test
    void unCompteSansProfilLolSurLaPlateformeDonneJoueurIntrouvable() {
        // Le compte Riot est mondial (Account-V1, ici via americas), mais pas de profil LoL sur NA1
        riot.expect(requestTo("https://americas.api.riotgames.com/riot/account/v1/accounts/by-riot-id/Joueur%20Test/EUW"))
                .andRespond(withSuccess(RiotFixtures.read(RiotFixtures.ACCOUNT), MediaType.APPLICATION_JSON));
        riot.expect(requestTo("https://na1.api.riotgames.com/lol/summoner/v4/summoners/by-puuid/" + RiotFixtures.PUUID))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.getOrFetch(Platform.NA1, "Joueur Test", "EUW"))
                .isInstanceOf(PlayerNotFoundException.class)
                .hasMessageContaining("n'a pas de profil League of Legends sur NA (Amérique du Nord)");
        riot.verify();
        assertThat(repository.count()).isZero();
    }

    @Test
    void unePlateformeSeaPasseParAsiaPourLeCompte() {
        // Account-V1 n'est pas servi sur sea (docs/riot-api.md §3)
        riotRespondsWithPlayer(
                "https://asia.api.riotgames.com/riot/account/v1/accounts/by-riot-id/Joueur%20Test/EUW",
                "https://sg2.api.riotgames.com/lol/summoner/v4/summoners/by-puuid/" + RiotFixtures.PUUID);

        Player player = service.getOrFetch(Platform.SG2, "Joueur Test", "EUW");

        riot.verify();
        assertThat(player.getPlatform()).isEqualTo(Platform.SG2);
    }

    @Test
    void uneSaisieVideEstRefuseeSansAppelerRiot() {
        assertThatThrownBy(() -> service.getOrFetch(Platform.EUW1, "  ", "EUW"))
                .isInstanceOf(InvalidRequestException.class).hasMessage("Le nom du Riot ID ne doit pas être vide.");
        assertThatThrownBy(() -> service.getOrFetch(Platform.EUW1, "Joueur Test", null))
                .isInstanceOf(InvalidRequestException.class).hasMessage("Le tag du Riot ID ne doit pas être vide.");
        riot.verify();
    }

    @Test
    void uneErreurRiotRemonteSansRienEnregistrer() {
        riot.expect(requestTo(ACCOUNT_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> service.getOrFetch(Platform.EUW1, "Joueur Test", "EUW"))
                .isInstanceOf(RiotUnavailableException.class);
        assertThat(repository.count()).isZero();
    }

    /**
     * Deux recherches simultanées du même nouveau joueur : la seconde échoue à l'INSERT (PUUID déjà présent) et
     * renvoie le joueur enregistré par la première. Impossible à provoquer de façon fiable avec une vraie base,
     * d'où un repository simulé.
     */
    @Test
    void deuxRecherchesSimultaneesNeProvoquentPasDErreur() {
        Player savedByOtherRequest = new Player(RiotFixtures.PUUID, "Joueur Test", "EUW", Platform.EUW1, 512, 7, NOW);
        PlayerRepository racingRepository = mock(PlayerRepository.class);
        when(racingRepository.findByRiotId(any(), any(), any())).thenReturn(Optional.empty());
        when(racingRepository.findById(RiotFixtures.PUUID))
                .thenReturn(Optional.empty())                 // pas encore là au moment de la vérification…
                .thenReturn(Optional.of(savedByOtherRequest)); // …mais présent juste après l'échec de l'INSERT
        when(racingRepository.save(any(Player.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates player_pkey"));
        riotRespondsWithAccountAndSummoner(ACCOUNT_URL, SUMMONER_URL);
        LeagueService mockedLeagueService = mock(LeagueService.class);

        Player player = new PlayerService(riotClient, racingRepository, mockedLeagueService,
                Clock.fixed(NOW, ZoneOffset.UTC))
                .getOrFetch(Platform.EUW1, "Joueur Test", "EUW");

        assertThat(player).isSameAs(savedByOtherRequest);
        // Les rangs sont quand même enregistrés (LeagueService gère lui aussi les écritures simultanées)
        verify(mockedLeagueService).save(eq(RiotFixtures.PUUID), anyList(), eq(NOW));
    }
}
