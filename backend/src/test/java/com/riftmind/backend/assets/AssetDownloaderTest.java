package com.riftmind.backend.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Téléchargement avec le vrai client HTTP de l'application, vers un faux Data Dragon local. */
class AssetDownloaderTest {

    private static final String KALISTA = "ddragon/cdn/16.21.1/img/champion/Kalista.png";
    private static final String KALISTA_URL = "/ddragon/cdn/16.21.1/img/champion/Kalista.png";

    private final FakeAssetServer server = FakeAssetServer.start();
    private final AssetProperties properties = server.properties(Path.of("inutilise"));
    private final AssetDownloader downloader = FakeAssetServer.downloader(properties);

    @AfterEach
    void stop() {
        server.close();
    }

    private byte[] download(String path) {
        return downloader.download(AssetPath.parse(path, "fr_FR").orElseThrow(), properties);
    }

    @Test
    void telechargeUneImage() {
        assertThat(download(KALISTA)).isEqualTo(FakeAssetServer.read("icon.png"));
    }

    @Test
    void nEnvoieNiLaCleRiotNiAutreChoseQueLeNecessaire() {
        download(KALISTA);

        var headers = server.requests().getFirst().headers();
        assertThat(headers.containsKey("X-riot-token")).isFalse();
        assertThat(headers.getFirst("User-agent")).isEqualTo("RiftMind");
        assertThat(headers.containsKey("Cookie")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 404})
    void unFichierAbsentEstSignaleCommeTel(int status) {
        server.respond(KALISTA_URL, status);

        assertThatThrownBy(() -> download(KALISTA))
                .isInstanceOfSatisfying(AssetDownloadException.class, e -> assertThat(e.notFound()).isTrue());
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 503, 429})
    void uneErreurDuServeurEstPassagere(int status) {
        server.respond(KALISTA_URL, status);

        assertThatThrownBy(() -> download(KALISTA))
                .isInstanceOfSatisfying(AssetDownloadException.class, e -> assertThat(e.notFound()).isFalse());
    }

    @Test
    void neSuitPasLesRedirections() {
        server.respond(KALISTA_URL, 302, new byte[0], Map.of("Location", "http://169.254.169.254/latest/meta-data"));

        assertThatThrownBy(() -> download(KALISTA)).isInstanceOf(AssetDownloadException.class);
        assertThat(server.total()).isEqualTo(1);
    }

    @Test
    void unServeurInjoignableEstUneErreurPassagere() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        AssetProperties unreachable = AssetProperties.of(Path.of("x"), "http://127.0.0.1:" + closedPort);

        assertThatThrownBy(() -> FakeAssetServer.downloader(unreachable)
                .download(AssetPath.parse(KALISTA, "fr_FR").orElseThrow(), unreachable))
                .isInstanceOfSatisfying(AssetDownloadException.class, e -> assertThat(e.notFound()).isFalse());
    }

    @Test
    void unServeurTropLentEstAbandonneApresLeDelai() {
        AssetProperties impatient = new AssetProperties("x", server.baseUrl() + "/ddragon", "fr_FR", false,
                Duration.ofHours(1), 2, 4, Duration.ofSeconds(5), Duration.ofMillis(200), Duration.ofSeconds(60));
        server.delay(Duration.ofSeconds(2));

        assertThatThrownBy(() -> FakeAssetServer.downloader(impatient)
                .download(AssetPath.parse(KALISTA, "fr_FR").orElseThrow(), impatient))
                .isInstanceOfSatisfying(AssetDownloadException.class, e -> assertThat(e.notFound()).isFalse());
    }

    @Test
    void refuseUneImageTropGrosse() {
        byte[] huge = Arrays.copyOf(FakeAssetServer.read("icon.png"), AssetDownloader.MAX_IMAGE_BYTES + 1);
        server.respond(KALISTA_URL, 200, huge, Map.of());

        assertThatThrownBy(() -> download(KALISTA)).hasMessageContaining("trop gros");
    }

    @Test
    void refuseUneImageQuiNEnEstPasUne() {
        server.respond(KALISTA_URL, 200, "<html><script>alert(1)</script></html>");

        assertThatThrownBy(() -> download(KALISTA)).hasMessageContaining("n'est pas une image PNG");
    }

    @Test
    void refuseDesDonneesQuiNeSontPasDuJson() {
        server.respond("/ddragon/cdn/16.21.1/data/fr_FR/item.json", 200, "<html>maintenance</html>");

        assertThatThrownBy(() -> download("ddragon/cdn/16.21.1/data/fr_FR/item.json"))
                .hasMessageContaining("n'est pas du JSON");
    }
}
