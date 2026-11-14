package com.riftmind.backend.riot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** Mesures des appels à Riot (tâche 8.2) : appels, 429, refus et attente du limiteur, résumé des logs. */
class RiotMetricsTest {

    private static final String EUW1 = "euw1.api.riotgames.com";
    private static final String EUROPE = "europe.api.riotgames.com";
    private static final String STATUS_PATH = "/lol/status/v4/platform-data";

    private SimpleMeterRegistry registry;
    private RiotMetrics metrics;
    private MockRestServiceServer server;
    private RiotRateLimiter rateLimiter;
    private RiotClient client;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new RiotMetrics(registry);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RiotProperties properties = new RiotProperties("RGAPI-test-key-not-a-real-key",
                RiotProperties.RateLimit.defaults(), Duration.ofMinutes(2), Duration.ofSeconds(5),
                Duration.ofSeconds(10));
        rateLimiter = new RiotRateLimiter(properties.rateLimit());
        client = new RiotClient(builder, properties, rateLimiter, metrics);
    }

    private double calls(String host, String status) {
        var counter = registry.find(RiotMetrics.CALLS).tag("host", host).tag("status", status).counter();
        return counter == null ? 0 : counter.count();
    }

    private double rateLimited(String host, String source, String type) {
        var counter = registry.find(RiotMetrics.RATE_LIMITED)
                .tag("host", host).tag("source", source).tag("type", type).counter();
        return counter == null ? 0 : counter.count();
    }

    private void respond(String host, HttpStatus status) {
        server.expect(requestTo("https://" + host + STATUS_PATH))
                .andRespond(status.is2xxSuccessful()
                        ? withSuccess("{}", MediaType.APPLICATION_JSON)
                        : withStatus(status));
    }

    @Test
    void compteLesAppelsParHoteEtParStatut() {
        respond(EUW1, HttpStatus.OK);
        respond(EUW1, HttpStatus.OK);
        respond(EUW1, HttpStatus.NOT_FOUND);

        client.get(Platform.EUW1, STATUS_PATH, String.class);
        client.get(Platform.EUW1, STATUS_PATH, String.class);
        assertThatThrownBy(() -> client.get(Platform.EUW1, STATUS_PATH, String.class))
                .isInstanceOf(RiotNotFoundException.class);

        assertThat(calls(EUW1, "200")).isEqualTo(2);
        assertThat(calls(EUW1, "404")).isEqualTo(1);
    }

    @Test
    void compteLes429DeRiotAvecLeurType() {
        server.expect(requestTo("https://" + EUW1 + STATUS_PATH))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, "1").header("X-Rate-Limit-Type", "application"));

        assertThatThrownBy(() -> client.get(Platform.EUW1, STATUS_PATH, String.class))
                .isInstanceOf(RiotRateLimitException.class);

        assertThat(calls(EUW1, "429")).isEqualTo(1);
        assertThat(rateLimited(EUW1, "riot", "application")).isEqualTo(1);
    }

    @Test
    void compteLesRefusDuLimiteurLocalSansAppelerRiot() {
        // Hôte bloqué plus longtemps que l'attente maximale (5 s) : refus immédiat, aucune requête
        rateLimiter.blockHost(EUW1, Duration.ofMinutes(1));

        assertThatThrownBy(() -> client.get(Platform.EUW1, STATUS_PATH, String.class))
                .isInstanceOf(RiotRateLimitException.class);

        server.verify();
        assertThat(rateLimited(EUW1, "local", RiotRateLimiter.LOCAL_LIMIT_TYPE)).isEqualTo(1);
        assertThat(calls(EUW1, "429")).isZero();
    }

    @Test
    void mesureLAttenteImposeeParLeLimiteur() {
        rateLimiter.blockHost(EUW1, Duration.ofMillis(300));
        respond(EUW1, HttpStatus.OK);

        client.get(Platform.EUW1, STATUS_PATH, String.class);

        var timer = registry.find(RiotMetrics.LIMITER_WAIT).tag("host", EUW1).timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(250);
    }

    @Test
    void compteLesAppelsSansReponse() {
        server.expect(requestTo("https://" + EUW1 + STATUS_PATH))
                .andRespond(withException(new IOException("Connection reset")));

        assertThatThrownBy(() -> client.get(Platform.EUW1, STATUS_PATH, String.class))
                .isInstanceOf(RiotUnavailableException.class);

        assertThat(calls(EUW1, "IO_ERROR")).isEqualTo(1);
    }

    @Test
    void resumeLisiblePuisRemiseAZero() {
        respond(EUROPE, HttpStatus.OK);
        respond(EUW1, HttpStatus.OK);
        respond(EUW1, HttpStatus.OK);
        client.get(RegionalRoute.EUROPE, STATUS_PATH, String.class);
        client.get(Platform.EUW1, STATUS_PATH, String.class);
        client.get(Platform.EUW1, STATUS_PATH, String.class);
        metrics.recordRiotRateLimit(EUW1, "method");

        assertThat(metrics.summaryAndReset()).isEqualTo(
                "3 appels (europe.api.riotgames.com : 1, euw1.api.riotgames.com : 2)"
                        + " · limites Riot (429) : 1 (euw1.api.riotgames.com : 1)"
                        + " · refus du limiteur local : 0");
        // Rien depuis le dernier résumé : pas de ligne de log
        assertThat(metrics.summaryAndReset()).isNull();
        // Les compteurs Micrometer, eux, ne sont jamais remis à zéro
        assertThat(calls(EUW1, "200")).isEqualTo(2);
    }
}
