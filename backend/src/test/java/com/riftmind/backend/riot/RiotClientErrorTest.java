package com.riftmind.backend.riot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.net.SocketTimeoutException;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Conversion des erreurs de l'API Riot en exceptions métier (tâche 1.4). */
class RiotClientErrorTest {

    private static final String FAKE_KEY = "RGAPI-test-key-not-a-real-key";
    private static final String URL = "https://euw1.api.riotgames.com/lol/summoner/v4/summoners/by-puuid/abc";

    private MockRestServiceServer server;
    private RiotClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RiotClient(builder, new RiotProperties(FAKE_KEY, new RiotProperties.RateLimit(null, null),
                Duration.ofMinutes(2), Duration.ofSeconds(5), Duration.ofSeconds(10)));
    }

    private Object callSummoner() {
        return client.get(Platform.EUW1, "/lol/summoner/v4/summoners/by-puuid/{puuid}", String.class, "abc");
    }

    @Test
    void erreur404DevientRiotNotFound() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"status\":{\"message\":\"Data not found - summoner\",\"status_code\":404}}"));

        assertThatThrownBy(this::callSummoner)
                .isInstanceOfSatisfying(RiotNotFoundException.class, e -> {
                    assertThat(e.riotStatus()).isEqualTo(404);
                    assertThat(e.host()).isEqualTo("euw1.api.riotgames.com");
                    assertThat(e.getMessage()).contains("Data not found - summoner");
                });
    }

    @Test
    void erreur429DevientRiotRateLimitAvecRetryAfter() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "7")
                .header("X-Rate-Limit-Type", "application"));

        assertThatThrownBy(this::callSummoner)
                .isInstanceOfSatisfying(RiotRateLimitException.class, e -> {
                    assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(7));
                    assertThat(e.limitType()).isEqualTo("application");
                    assertThat(e.host()).isEqualTo("euw1.api.riotgames.com");
                });
    }

    @Test
    void erreur429DeServiceSansRetryAfterUtiliseLeDelaiParDefaut() {
        // Limite d'un service Riot sous-jacent : pas d'en-tête X-Rate-Limit-Type (docs/riot-api.md §2)
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(this::callSummoner)
                .isInstanceOfSatisfying(RiotRateLimitException.class, e -> {
                    assertThat(e.retryAfter()).isEqualTo(RiotRateLimitException.DEFAULT_RETRY_AFTER);
                    assertThat(e.limitType()).isNull();
                    assertThat(e.getMessage()).contains("service");
                });
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void erreur401Ou403DevientRiotApiKey(int status) {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.valueOf(status)));

        assertThatThrownBy(this::callSummoner)
                .isInstanceOfSatisfying(RiotApiKeyException.class, e -> {
                    assertThat(e.riotStatus()).isEqualTo(status);
                    assertThat(e.getMessage()).contains("RIOT_API_KEY").doesNotContain(FAKE_KEY);
                });
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 504})
    void erreur5xxDevientRiotUnavailable(int status) {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.valueOf(status)));

        assertThatThrownBy(this::callSummoner)
                .isInstanceOfSatisfying(RiotUnavailableException.class,
                        e -> assertThat(e.riotStatus()).isEqualTo(status));
    }

    @Test
    void timeoutOuErreurReseauDevientRiotUnavailable() {
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(this::callSummoner)
                .isInstanceOfSatisfying(RiotUnavailableException.class, e -> {
                    assertThat(e.riotStatus()).isZero();
                    assertThat(e.host()).isEqualTo("euw1.api.riotgames.com");
                });
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 405, 415})
    void autreErreurClientDevientRiotApiExceptionGenerique(int status) {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.valueOf(status)).body("bad request"));

        assertThatThrownBy(this::callSummoner)
                .isExactlyInstanceOf(RiotApiException.class)
                .hasMessageContaining(String.valueOf(status));
    }

    @Test
    void lectureDuRetryAfter() {
        assertThat(RiotClient.parseRetryAfter("12")).isEqualTo(Duration.ofSeconds(12));
        assertThat(RiotClient.parseRetryAfter(" 3 ")).isEqualTo(Duration.ofSeconds(3));
        assertThat(RiotClient.parseRetryAfter(null)).isNull();
        assertThat(RiotClient.parseRetryAfter("abc")).isNull();
        assertThat(RiotClient.parseRetryAfter("-1")).isNull();
    }
}
