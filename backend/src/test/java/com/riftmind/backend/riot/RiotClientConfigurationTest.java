package com.riftmind.backend.riot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

/** Vérifie que le timeout de lecture est réellement appliqué, contre un serveur local volontairement lent. */
class RiotClientConfigurationTest {

    private final CountDownLatch release = new CountDownLatch(1);
    private HttpServer slowServer;

    @BeforeEach
    void startSlowServer() throws IOException {
        slowServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        slowServer.createContext("/", exchange -> {
            try {
                // Ne répond qu'au bout de 5 s (ou à la fin du test) : bien après le timeout de lecture
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        slowServer.start();
    }

    @AfterEach
    void stopSlowServer() {
        release.countDown();
        slowServer.stop(0);
    }

    @Test
    void abandonneUnAppelTropLent() {
        RiotProperties properties = new RiotProperties("RGAPI-test", RiotProperties.RateLimit.defaults(),
                Duration.ofMinutes(2), Duration.ofSeconds(1), Duration.ofMillis(300));
        RestClient restClient = RestClient.builder()
                .requestFactory(RiotClientConfiguration.requestFactory(properties))
                .build();
        String url = "http://127.0.0.1:" + slowServer.getAddress().getPort() + "/slow";

        long start = System.nanoTime();
        assertThatThrownBy(() -> restClient.get().uri(url).retrieve().body(String.class))
                .isInstanceOf(ResourceAccessException.class);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        // Abandon peu après les 300 ms configurées, sans attendre la réponse du serveur (5 s)
        assertThat(elapsed).isBetween(Duration.ofMillis(250), Duration.ofSeconds(2));
    }
}
