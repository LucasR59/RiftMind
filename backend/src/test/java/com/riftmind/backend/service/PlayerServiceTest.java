package com.riftmind.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import com.riftmind.backend.TestcontainersConfiguration;
import com.riftmind.backend.exception.PlayerNotFoundException;
import com.riftmind.backend.model.Player;
import com.riftmind.backend.repository.PlayerRepository;
import com.riftmind.backend.riot.Platform;
import com.riftmind.backend.riot.RiotClient;
import com.riftmind.backend.riot.RiotFixtures;
import com.riftmind.backend.riot.RiotProperties;
import com.riftmind.backend.riot.RiotRateLimiter;
import com.riftmind.backend.riot.RiotUnavailableException;

/**
 * Recherche d'un joueur (tâche 2.4).
 *
 * <p>Riot est simulé par {@link MockRestServiceServer} avec les vraies réponses anonymisées
 * ({@link RiotFixtures}) : les URL appelées sont vérifiées, et toute requête inattendue fait échouer le test.
 * La base est un vrai PostgreSQL, sans transaction de test : chaque accès au repository est validé, comme en
 * production, et la table est vidée avant chaque test.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(TestcontainersConfiguration.class)
class PlayerServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-01T20:00:00Z");
    private static final String EUROPE = "https://europe.api.riotgames.com";
    private static final String EUW1 = "https://euw1.api.riotgames.com";
    private static final String ACCOUNT_URL = EUROPE + "/riot/account/v1/accounts/by-riot-id/Joueur%20Test/EUW";
    private static final String SUMMONER_URL = EUW1 + "/lol/summoner/v4/summoners/by-puuid/" + RiotFixtures.PUUID;

    @Autowired
    private PlayerRepository repository;

    private MockRestServiceServer riot;
    private RiotClient riotClient;
    private PlayerService service;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        RestClient.Builder builder = RestClient.builder();
        riot = MockRestServiceServer.bindTo(builder).build();
        RiotProperties properties = new RiotProperties("RGAPI-test-key-not-a-real-key",
                RiotProperties.RateLimit.defaults(), Duration.ofMinutes(2), Duration.ofSeconds(5),
                Duration.ofSeconds(10));
        riotClient = new RiotClient(builder, properties, new RiotRateLimiter(properties.rateLimit()));
        service = new PlayerService(riotClient, repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void riotRespondsWithAccountAndSummoner(String accountUrl, String summonerUrl) {
        riot.expect(requestTo(accountUrl)).andExpect(header("X-Riot-Token", "RGAPI-test-key-not-a-real-key"))
                .andRespond(withSuccess(RiotFixtures.read(RiotFixtures.ACCOUNT), MediaType.APPLICATION_JSON));
        riot.expect(requestTo(summonerUrl))
                .andRespond(withSuccess(RiotFixtures.read(RiotFixtures.SUMMONER), MediaType.APPLICATION_JSON));
    }

    @Test
    void unNouveauJoueurEstRecupereChezRiotPuisEnregistre() {
        riotRespondsWithAccountAndSummoner(ACCOUNT_URL, SUMMONER_URL);

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
        riotRespondsWithAccountAndSummoner(
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
        riotRespondsWithAccountAndSummoner(ACCOUNT_URL, SUMMONER_URL);

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
        riotRespondsWithAccountAndSummoner(
                "https://asia.api.riotgames.com/riot/account/v1/accounts/by-riot-id/Joueur%20Test/EUW",
                "https://sg2.api.riotgames.com/lol/summoner/v4/summoners/by-puuid/" + RiotFixtures.PUUID);

        Player player = service.getOrFetch(Platform.SG2, "Joueur Test", "EUW");

        riot.verify();
        assertThat(player.getPlatform()).isEqualTo(Platform.SG2);
    }

    @Test
    void uneSaisieVideEstRefuseeSansAppelerRiot() {
        assertThatThrownBy(() -> service.getOrFetch(Platform.EUW1, "  ", "EUW"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("gameName");
        assertThatThrownBy(() -> service.getOrFetch(Platform.EUW1, "Joueur Test", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tagLine");
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

        Player player = new PlayerService(riotClient, racingRepository, Clock.fixed(NOW, ZoneOffset.UTC))
                .getOrFetch(Platform.EUW1, "Joueur Test", "EUW");

        assertThat(player).isSameAs(savedByOtherRequest);
    }
}
