package com.riftmind.backend.assets;

import static com.riftmind.backend.assets.FakeAssetServer.DATA_DRAGON_IMAGES;
import static com.riftmind.backend.assets.FakeAssetServer.VERSION;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.riftmind.backend.assets.AssetSync.Result;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Copie de Data Dragon depuis un faux serveur local (fichiers de src/test/resources/assets). */
class AssetSyncTest {

    private static final String KALISTA = "ddragon/cdn/16.21.1/img/champion/Kalista.png";
    private static final String VERSIONS_URL = "/ddragon/api/versions.json";

    @TempDir
    Path root;

    private final FakeAssetServer server = FakeAssetServer.start();

    @AfterEach
    void stop() {
        server.close();
    }

    private AssetSync sync(AssetStore store) {
        AssetProperties properties = server.properties(root);
        return new AssetSync(properties, store, FakeAssetServer.downloader(properties));
    }

    @Test
    void copieEtPublieLaDerniereVersion() throws Exception {
        AssetStore store = new AssetStore(root);

        assertThat(sync(store).sync()).isEqualTo(Result.PUBLISHED);

        assertThat(store.publishedVersions()).containsExactly(VERSION);
        assertThat(store.read("ddragon/cdn/16.21.1/data/fr_FR/champion.json"))
                .isEqualTo(FakeAssetServer.read("ddragon/cdn/16.21.1/data/fr_FR/champion.json"));
        assertThat(store.exists(KALISTA)).isTrue();
        assertThat(store.exists("ddragon/cdn/16.21.1/img/item/3087.png")).isTrue();
        assertThat(store.exists("ddragon/cdn/16.21.1/img/spell/SummonerFlash.png")).isTrue();
        assertThat(store.exists("ddragon/cdn/img/perk-images/Styles/7200_Domination.png")).isTrue();
        assertThat(store.exists("ddragon/cdn/img/perk-images/Styles/Sorcery/NimbusCloak/6361.png")).isTrue();
        // versions.json + 4 fichiers de données + les images, rien d'autre (les emblèmes de rang sont livrés)
        assertThat(server.total()).isEqualTo(1 + 4 + DATA_DRAGON_IMAGES);
    }

    @Test
    void unSecondPassageNeDemandeQueLaListeDesVersions() {
        AssetSync sync = sync(new AssetStore(root));
        sync.sync();
        server.clearRequests();

        assertThat(sync.sync()).isEqualTo(Result.UP_TO_DATE);

        assertThat(server.requests()).extracting(FakeAssetServer.Request::path).containsExactly(VERSIONS_URL);
    }

    @Test
    void uneErreurPassagereEmpecheLaPublicationPuisLePassageSuivantReprend() {
        AssetStore store = new AssetStore(root);
        AssetSync sync = sync(store);
        server.respond("/" + KALISTA, 503);

        assertThat(sync.sync()).isEqualTo(Result.INCOMPLETE);
        assertThat(store.publishedVersions()).isEmpty();
        assertThat(store.exists("ddragon/cdn/16.21.1/img/champion/Jayce.png")).isTrue();

        server.reset("/" + KALISTA);
        server.clearRequests();
        assertThat(sync.sync()).isEqualTo(Result.PUBLISHED);

        // Seul ce qui manquait est redemandé
        assertThat(server.requests()).extracting(FakeAssetServer.Request::path)
                .containsExactlyInAnyOrder(VERSIONS_URL, "/" + KALISTA);
        assertThat(store.publishedVersions()).containsExactly(VERSION);
    }

    @Test
    void uneImageAbsenteChezRiotNEmpechePasLaPublication() {
        AssetStore store = new AssetStore(root);
        server.respond("/" + KALISTA, 403);

        assertThat(sync(store).sync()).isEqualTo(Result.PUBLISHED);

        assertThat(store.exists(KALISTA)).isFalse();
        assertThat(store.publishedVersions()).containsExactly(VERSION);
    }

    @Test
    void desDonneesIllisiblesEmpechentLaPublication() {
        AssetStore store = new AssetStore(root);
        server.respond("/ddragon/cdn/16.21.1/data/fr_FR/item.json", 200, "<html>maintenance</html>");

        assertThat(sync(store).sync()).isEqualTo(Result.INCOMPLETE);

        assertThat(store.publishedVersions()).isEmpty();
        assertThat(store.exists("ddragon/cdn/16.21.1/data/fr_FR/item.json")).isFalse();
    }

    @Test
    void dataDragonEnPanneGardeLaVersionDejaPubliee() {
        AssetStore store = new AssetStore(root);
        AssetSync sync = sync(store);
        sync.sync();
        server.respond(VERSIONS_URL, 503);

        assertThat(sync.sync()).isEqualTo(Result.INCOMPLETE);

        assertThat(store.publishedVersions()).containsExactly(VERSION);
        assertThat(store.exists(KALISTA)).isTrue();
    }

    @Test
    void uneNouvelleVersionEstCopieeEtLaPlusAncienneSupprimee() {
        AssetStore store = new AssetStore(root);
        AssetSync sync = sync(store);
        sync.sync();
        server.latestVersion("16.22.1");
        sync.sync();
        server.latestVersion("16.23.1");

        assertThat(sync.sync()).isEqualTo(Result.PUBLISHED);

        // assets.keep-versions = 2
        assertThat(store.publishedVersions()).containsExactly("16.23.1", "16.22.1");
        assertThat(root.resolve("ddragon/cdn/16.21.1")).doesNotExist();
        assertThat(store.exists("ddragon/cdn/16.23.1/img/champion/Kalista.png")).isTrue();
        // Les runes ne sont pas versionnées : gardées et non retéléchargées
        assertThat(server.count("/ddragon/cdn/img/perk-images/Styles/7200_Domination.png")).isEqualTo(1);
    }

    @Test
    void uneVersionInvalideEstIgnoree() {
        AssetStore store = new AssetStore(root);
        server.respond(VERSIONS_URL, 200, "[\"../../../etc\"]");

        assertThat(sync(store).sync()).isEqualTo(Result.INCOMPLETE);

        assertThat(store.publishedVersions()).isEmpty();
        assertThat(server.requests()).extracting(FakeAssetServer.Request::path)
                .noneMatch(path -> path.startsWith("/ddragon/cdn/"));
    }

    @Test
    void unNomDangereuxDansDataDragonNeDevientJamaisUnChemin() throws Exception {
        JsonMapper json = JsonMapper.builder().build();
        Map<String, JsonNode> data = Map.of(
                "champion", json.readTree("""
                        {"data": {"Kalista": {"id": "Kalista"}, "Evil": {"id": "../../../../evil"},
                                  "Vide": {}, "Nombre": {"id": 42}}}"""),
                "item", json.readTree("""
                        {"data": {"1001": {}, "../../evil": {}}}"""),
                "summoner", json.readTree("""
                        {"data": {"Flash": {"image": {"full": "SummonerFlash.png"}},
                                  "Evil": {"image": {"full": "../../evil.png"}}}}"""),
                "runesReforged", json.readTree("""
                        [{"icon": "perk-images/Styles/7200_Domination.png",
                          "slots": [{"runes": [{"icon": "perk-images/../../../../evil.png"}, {"icon": "/etc/passwd"}]}]}]
                        """));

        var images = sync(new AssetStore(root)).imagesOf(VERSION, data);

        assertThat(images).extracting(AssetPath::path).containsExactly(
                KALISTA,
                "ddragon/cdn/16.21.1/img/item/1001.png",
                "ddragon/cdn/16.21.1/img/spell/SummonerFlash.png",
                "ddragon/cdn/img/perk-images/Styles/7200_Domination.png");
    }

    @Test
    void lesFichiersCopiesSontLesSeulsFichiersDuDossier() throws Exception {
        sync(new AssetStore(root)).sync();

        try (Stream<Path> files = Files.walk(root)) {
            assertThat(files.filter(Files::isRegularFile).map(root::relativize).map(Path::toString)
                    .map(path -> path.replace('\\', '/')))
                    .allSatisfy(path -> assertThat(
                            AssetPath.parse(path, "fr_FR").isPresent() || path.endsWith("/.complete"))
                            .as(path).isTrue())
                    .hasSize(4 + DATA_DRAGON_IMAGES + 1);
        }
    }

    @Test
    void deuxPassagesSimultanesNeCopientQuUneFois() throws Exception {
        AssetSync sync = sync(new AssetStore(root));
        server.delay(Duration.ofMillis(5));

        Thread other = Thread.ofVirtual().start(sync::sync);
        Result result = sync.sync();
        other.join();

        assertThat(server.count("/" + KALISTA)).isEqualTo(1);
        assertThat(result).isIn(Result.PUBLISHED, Result.ALREADY_RUNNING, Result.UP_TO_DATE);
    }
}
