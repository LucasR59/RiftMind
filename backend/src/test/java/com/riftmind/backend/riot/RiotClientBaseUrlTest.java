package com.riftmind.backend.riot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.riftmind.backend.riot.dto.AccountDto;

/**
 * Adresse de remplacement de l'API Riot ({@code riot.base-url}, tâche 8.1) : les tests de bout en bout appellent une
 * fausse API WireMock, l'hôte Riot devenant le premier segment du chemin.
 */
class RiotClientBaseUrlTest {

    private static final String KEY = "RGAPI-test-key-not-a-real-key";

    private MockRestServiceServer server;
    private RiotRateLimiter rateLimiter;

    private RiotClient client(String baseUrl) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RiotProperties properties = new RiotProperties(KEY, RiotProperties.RateLimit.defaults(),
                Duration.ofMinutes(2), Duration.ofSeconds(5), Duration.ofSeconds(10), baseUrl);
        rateLimiter = new RiotRateLimiter(properties.rateLimit());
        return new RiotClient(builder, properties, rateLimiter);
    }

    @Test
    void sansAdresseDeRemplacementAppelleLaVraieApi() {
        RiotProperties properties = new RiotProperties(KEY, RiotProperties.RateLimit.defaults(),
                Duration.ofMinutes(2), Duration.ofSeconds(5), Duration.ofSeconds(10), "  ");

        assertThat(properties.baseUrl()).isNull();
    }

    @Test
    void metLHoteRiotEnPremierSegmentDuChemin() {
        RiotClient client = client("http://wiremock:8080/");
        server.expect(requestTo("http://wiremock:8080/europe.api.riotgames.com"
                        + "/riot/account/v1/accounts/by-riot-id/Joueur%20Test/EUW"))
                .andExpect(header("X-Riot-Token", KEY))
                .andRespond(withSuccess(RiotFixtures.read(RiotFixtures.ACCOUNT), MediaType.APPLICATION_JSON));

        AccountDto account = client.get(RegionalRoute.EUROPE,
                "/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}", AccountDto.class, "Joueur Test", "EUW");

        assertThat(account.puuid()).isEqualTo(RiotFixtures.PUUID);
        server.verify();
    }

    @Test
    void unPrefixeDeCheminDansLAdresseEstGarde() {
        RiotClient client = client("http://localhost:9000/riot-mock");
        server.expect(requestTo("http://localhost:9000/riot-mock/euw1.api.riotgames.com/lol/status/v4/platform-data"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.get(Platform.EUW1, "/lol/status/v4/platform-data", String.class);

        server.verify();
    }

    @Test
    void unQuatreCentVingtNeufBloqueLHoteRiotEtNonLaFausseApi() {
        RiotClient client = client("http://wiremock:8080");
        server.expect(requestTo("http://wiremock:8080/euw1.api.riotgames.com/lol/status/v4/platform-data"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "30"));

        assertThatThrownBy(() -> client.get(Platform.EUW1, "/lol/status/v4/platform-data", String.class))
                .isInstanceOfSatisfying(RiotRateLimitException.class,
                        e -> assertThat(e.host()).isEqualTo("euw1.api.riotgames.com"));

        // euw1 est bloqué ; les autres hôtes Riot, servis par la même fausse API, ne le sont pas
        assertThatThrownBy(() -> rateLimiter.acquire("euw1.api.riotgames.com"))
                .isInstanceOf(RiotRateLimitException.class);
        rateLimiter.acquire("europe.api.riotgames.com").close();
        rateLimiter.acquire("wiremock").close();
    }
}
