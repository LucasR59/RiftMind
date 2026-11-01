package com.riftmind.backend.riot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Le {@link RiotClient} passe par le limiteur de requêtes et réagit aux 429 (tâche 1.5). */
@ExtendWith(OutputCaptureExtension.class)
class RiotClientRateLimitTest {

    private static final String EUW_URL = "https://euw1.api.riotgames.com/lol/status/v4/platform-data";
    private static final String NA_URL = "https://na1.api.riotgames.com/lol/status/v4/platform-data";

    private MockRestServiceServer server;
    private RiotClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        RiotProperties properties = new RiotProperties("RGAPI-test-key-not-a-real-key",
                RiotProperties.RateLimit.defaults(), Duration.ofMinutes(2), Duration.ofSeconds(5),
                Duration.ofSeconds(10));
        client = new RiotClient(builder, properties, new RiotRateLimiter(properties.rateLimit()));
    }

    private String status(String platformUrl) {
        Platform platform = platformUrl.equals(EUW_URL) ? Platform.EUW1 : Platform.NA1;
        return client.get(platform, "/lol/status/v4/platform-data", String.class);
    }

    @Test
    void la21eRequeteDansLaMemeSecondeAttend() {
        server.expect(times(21), requestTo(EUW_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        long start = System.nanoTime();
        for (int i = 0; i < 20; i++) {
            status(EUW_URL);
        }
        Duration first20 = Duration.ofNanos(System.nanoTime() - start);
        status(EUW_URL);
        Duration all21 = Duration.ofNanos(System.nanoTime() - start);

        // Les 20 premières passent sans attendre, la 21ᵉ attend la fin de la seconde
        assertThat(first20).isLessThan(Duration.ofMillis(500));
        assertThat(all21).isGreaterThanOrEqualTo(Duration.ofMillis(990));
        server.verify();
    }

    @Test
    void apresUn429LHoteEstBloqueSansAppelerRiot(CapturedOutput output) {
        server.expect(times(1), requestTo(EUW_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "7")
                .header("X-Rate-Limit-Type", "application")
                .header("X-App-Rate-Limit-Count", "21:1,40:120")
                .header("X-Method-Rate-Limit-Count", "3:10"));
        server.expect(times(1), requestTo(NA_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> status(EUW_URL)).isInstanceOf(RiotRateLimitException.class);

        // Blocage de 7 s > attente max de 5 s : refus immédiat, sans nouvelle requête vers Riot
        long start = System.nanoTime();
        assertThatThrownBy(() -> status(EUW_URL))
                .isInstanceOfSatisfying(RiotRateLimitException.class, e -> {
                    assertThat(e.limitType()).isEqualTo(RiotRateLimiter.LOCAL_LIMIT_TYPE);
                    assertThat(e.retryAfter()).isBetween(Duration.ofSeconds(6), Duration.ofSeconds(7));
                });
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(500));

        // Les autres hôtes continuent de fonctionner
        assertThat(status(NA_URL)).isEqualTo("{}");
        server.verify();

        assertThat(output).contains("WARN").contains("429 sur euw1.api.riotgames.com")
                .contains("type : application").contains("bloqués pendant 7 s")
                .contains("app 21:1,40:120").contains("méthode 3:10");
    }
}
