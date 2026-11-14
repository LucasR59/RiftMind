package com.riftmind.backend.assets;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Fichiers servis, et téléchargement des icônes de profil à la première demande. */
class AssetServiceTest {

    private static final String ICON = "ddragon/cdn/16.21.1/img/profileicon/7.png";

    @TempDir
    Path root;

    private final FakeAssetServer server = FakeAssetServer.start();
    private final MutableClock clock = new MutableClock();
    private AssetService service;

    @BeforeEach
    void copy() throws Exception {
        AssetProperties properties = server.properties(root);
        AssetStore store = new AssetStore(root);
        AssetDownloader downloader = FakeAssetServer.downloader(properties);
        RankImages.install(store);
        new AssetSync(properties, store, downloader).sync();
        service = new AssetService(properties, store, downloader, clock);
        server.clearRequests();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private Optional<Path> find(String path) {
        return service.find(service.parse(path).orElseThrow());
    }

    @Test
    void sertLesFichiersCopiesSansAppelerDataDragon() {
        assertThat(find("ddragon/cdn/16.21.1/img/champion/Kalista.png")).isPresent();
        assertThat(find("ranks/silver.png")).isPresent();
        assertThat(find("ranks/unranked.png")).isPresent();
        assertThat(service.publishedVersions()).containsExactly("16.21.1");

        assertThat(server.total()).isZero();
    }

    @Test
    void uneImageNonCopieeNEstPasDemandeeADataDragon() {
        assertThat(find("ddragon/cdn/16.21.1/img/champion/Jinx.png")).isEmpty();
        assertThat(find("ddragon/cdn/16.21.1/img/item/9999.png")).isEmpty();

        assertThat(server.total()).isZero();
    }

    @Test
    void uneIconeDeProfilEstTelechargeeUneFoisPuisServieDepuisLeDisque() {
        assertThat(find(ICON)).isPresent();
        assertThat(find(ICON)).isPresent();

        assertThat(server.count("/" + ICON)).isEqualTo(1);
    }

    @Test
    void uneIconeDUneVersionNonPublieeNEstJamaisDemandee() {
        assertThat(find("ddragon/cdn/16.22.1/img/profileicon/7.png")).isEmpty();
        assertThat(find("ddragon/cdn/9999.1.1/img/profileicon/7.png")).isEmpty();

        assertThat(server.total()).isZero();
    }

    @Test
    void uneIconeInexistanteNEstPasRedemandeeAvantUneHeure() {
        server.respond("/" + ICON, 403);

        assertThat(find(ICON)).isEmpty();
        assertThat(find(ICON)).isEmpty();
        assertThat(server.count("/" + ICON)).isEqualTo(1);

        clock.advance(AssetService.MISSING_TTL.plusSeconds(1));
        server.reset("/" + ICON);
        assertThat(find(ICON)).isPresent();
        assertThat(server.count("/" + ICON)).isEqualTo(2);
    }

    @Test
    void uneErreurPassagereEstRetenteeALaDemandeSuivante() {
        server.respond("/" + ICON, 503);
        assertThat(find(ICON)).isEmpty();

        server.reset("/" + ICON);
        assertThat(find(ICON)).isPresent();
        assertThat(server.count("/" + ICON)).isEqualTo(2);
    }

    @Test
    void desDemandesSimultaneesNeDeclenchentQuUnTelechargement() throws Exception {
        server.delay(Duration.ofMillis(300));
        List<Future<Optional<Path>>> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 20; i++) {
                results.add(executor.submit(() -> find(ICON)));
            }
        }

        for (Future<Optional<Path>> result : results) {
            assertThat(result.get()).isPresent();
        }
        assertThat(server.count("/" + ICON)).isEqualTo(1);
    }

    @Test
    void auPlusQuatreTelechargementsSimultanes() throws Exception {
        server.delay(Duration.ofSeconds(1));
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 1; i <= 30; i++) {
                String icon = "ddragon/cdn/16.21.1/img/profileicon/" + i + ".png";
                executor.submit(() -> find(icon));
            }
        }

        // 4 à la fois, 1 s chacun, 2 s d'attente au plus : une partie des 30 est refusée (404) plutôt qu'attendue
        assertThat(server.total()).isLessThan(30).isGreaterThanOrEqualTo(AssetService.MAX_UPSTREAM_REQUESTS);
    }

    /** Horloge réglable. */
    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-14T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
