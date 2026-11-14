package com.riftmind.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.riftmind.backend.assets.AssetDownloader;
import com.riftmind.backend.assets.AssetProperties;
import com.riftmind.backend.assets.AssetService;
import com.riftmind.backend.assets.AssetStore;
import com.riftmind.backend.assets.AssetSync;
import com.riftmind.backend.assets.FakeAssetServer;
import com.riftmind.backend.assets.RankImages;

/** {@code /api/assets/…} : contenu, en-têtes de cache et de sécurité, chemins refusés. */
class AssetControllerTest {

    private static final String KALISTA = "/api/assets/ddragon/cdn/16.21.1/img/champion/Kalista.png";

    @TempDir
    Path root;

    private final FakeAssetServer server = FakeAssetServer.start();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        AssetProperties properties = server.properties(root);
        AssetStore store = new AssetStore(root);
        AssetDownloader downloader = FakeAssetServer.downloader(properties);
        RankImages.install(store);
        new AssetSync(properties, store, downloader).sync();
        server.clearRequests();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AssetController(new AssetService(properties, store, downloader,
                        Clock.systemUTC())))
                .build();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private static ResultMatcher securityHeaders() {
        return result -> {
            header().string("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox")
                    .match(result);
            header().string("X-Content-Type-Options", "nosniff").match(result);
            header().string("Cross-Origin-Resource-Policy", "same-origin").match(result);
        };
    }

    @Test
    void listeLesVersionsCopiees() throws Exception {
        mockMvc.perform(get("/api/assets/ddragon/versions.json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.contains("16.21.1")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=300, public"))
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(securityHeaders());
    }

    @Test
    void laListeDesVersionsInchangeeRepond304() throws Exception {
        String etag = mockMvc.perform(get("/api/assets/ddragon/versions.json")).andReturn()
                .getResponse().getHeader(HttpHeaders.ETAG);

        mockMvc.perform(get("/api/assets/ddragon/versions.json").header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified());
    }

    @Test
    void sertUneImageVersionneeEnCachePourUnAn() throws Exception {
        mockMvc.perform(get(KALISTA))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(FakeAssetServer.read("icon.png")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=31536000, public, immutable"))
                .andExpect(header().exists(HttpHeaders.LAST_MODIFIED))
                .andExpect(securityHeaders());

        assertThat(server.total()).isZero();
    }

    @Test
    void sertLesDonneesDUneVersion() throws Exception {
        mockMvc.perform(get("/api/assets/ddragon/cdn/16.21.1/data/fr_FR/champion.json"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.data.Kalista.name").value("Kalista"));
    }

    @Test
    void sertLesEmblemesDeRangLivresAvecLeBackend() throws Exception {
        for (String tier : List.of("iron", "silver", "emerald", "challenger", "unranked")) {
            mockMvc.perform(get("/api/assets/ranks/" + tier + ".png"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("image/png"))
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=86400, public"))
                    .andExpect(securityHeaders());
        }

        assertThat(server.total()).isZero();
    }

    @Test
    void uneImageNonModifieeRepond304() throws Exception {
        String lastModified = mockMvc.perform(get("/api/assets/ranks/gold.png")).andReturn()
                .getResponse().getHeader(HttpHeaders.LAST_MODIFIED);

        mockMvc.perform(get("/api/assets/ranks/gold.png")
                        .header(HttpHeaders.IF_MODIFIED_SINCE, lastModified))
                .andExpect(status().isNotModified());
    }

    @Test
    void repondAuxRequetesHead() throws Exception {
        mockMvc.perform(head(KALISTA))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
    }

    @Test
    void telechargeUneIconeDeProfilALaPremiereDemande() throws Exception {
        mockMvc.perform(get("/api/assets/ddragon/cdn/16.21.1/img/profileicon/7.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
        mockMvc.perform(get("/api/assets/ddragon/cdn/16.21.1/img/profileicon/7.png"))
                .andExpect(status().isOk());

        assertThat(server.count("/ddragon/cdn/16.21.1/img/profileicon/7.png")).isEqualTo(1);
    }

    @Test
    void unFichierAbsentRepond404SansCache() throws Exception {
        mockMvc.perform(get("/api/assets/ddragon/cdn/16.21.1/img/champion/Jinx.png"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().bytes(new byte[0]))
                .andExpect(securityHeaders());

        assertThat(server.total()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/assets/",
            "/api/assets/ddragon/cdn/16.21.1/.complete",
            "/api/assets/ddragon/cdn/16.21.1/img/champion/..%2F..%2F..%2F.complete",
            "/api/assets/ddragon/cdn/16.21.1/img/champion/%2e%2e/%2e%2e/%2e%2e/.complete",
            "/api/assets/ddragon/cdn/16.21.1/img/champion/..%5C..%5Csecret.png",
            "/api/assets/ddragon/api/versions.json",
            "/api/assets/ddragon/cdn/16.21.1/data/en_US/champion.json",
            "/api/assets/ranks/wood.png",
            "/api/assets/ranks/..%2Fddragon%2Fversions.json",
            "/api/assets/cdragon/ranked-emblem/emblem-gold.png",
            "/api/assets/ddragon/cdn/16.21.1/img/champion/Kalista.png.svg",
    })
    void unCheminHorsDeLaListeBlancheNeRenvoieRien(String path) throws Exception {
        MvcResult result = mockMvc.perform(get(URI.create(path))).andReturn();

        assertThat(result.getResponse().getStatus()).as(path).isEqualTo(404);
        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
        assertThat(server.total()).isZero();
    }
}
