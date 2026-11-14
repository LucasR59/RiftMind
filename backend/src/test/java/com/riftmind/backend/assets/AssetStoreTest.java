package com.riftmind.backend.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AssetStoreTest {

    @TempDir
    Path root;

    private AssetStore store() {
        return new AssetStore(root);
    }

    @Test
    void ecritPuisRetrouveUnFichier() throws IOException {
        AssetStore store = store();
        String path = "ddragon/cdn/16.21.1/img/champion/Kalista.png";

        store.write(path, bytes("image"));

        assertThat(store.find(path)).contains(root.resolve(path));
        assertThat(store.read(path)).isEqualTo(bytes("image"));
        assertThat(store.exists("ddragon/cdn/16.21.1/img/champion/Jinx.png")).isFalse();
    }

    @Test
    void lEcritureEstAtomiqueEtNeLaissePasDeFichierTemporaire() throws IOException {
        AssetStore store = store();
        String path = "ranks/gold.png";

        store.write(path, bytes("v1"));
        store.write(path, bytes("v2"));

        assertThat(store.read(path)).isEqualTo(bytes("v2"));
        try (Stream<Path> files = Files.list(root.resolve("ranks"))) {
            assertThat(files).containsExactly(root.resolve(path));
        }
    }

    @Test
    void refuseToutCheminQuiSortDuDossier() {
        AssetStore store = store();
        String outside = root.getParent().resolve("dehors.png").toString();

        for (String path : List.of("../dehors.png", "ddragon/../../dehors.png", outside, "", ".")) {
            assertThatThrownBy(() -> store.write(path, bytes("x")))
                    .as(path).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> store.find(path)).as(path).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(root.getParent().resolve("dehors.png")).doesNotExist();
    }

    @Test
    void neListeQueLesVersionsCompletesDeLaPlusRecenteALaPlusAncienne() throws IOException {
        AssetStore store = store();
        for (String version : List.of("16.9.1", "16.21.1", "17.1.1")) {
            store.write("ddragon/cdn/" + version + "/data/fr_FR/item.json", bytes("{}"));
            store.publish(version);
        }
        store.write("ddragon/cdn/17.2.1/data/fr_FR/item.json", bytes("{}")); // en cours de copie
        Files.createDirectories(root.resolve("ddragon/cdn/img/perk-images")); // pas une version

        assertThat(store.publishedVersions()).containsExactly("17.1.1", "16.21.1", "16.9.1");
        assertThat(store.isPublished("17.2.1")).isFalse();
    }

    @Test
    void supprimeLesAnciennesVersionsSansToucherAuxImagesNonVersionnees() throws IOException {
        AssetStore store = store();
        for (String version : List.of("16.20.1", "16.21.1", "16.22.1")) {
            store.write("ddragon/cdn/" + version + "/img/item/1001.png", bytes("x"));
            store.publish(version);
        }
        store.write("ddragon/cdn/img/perk-images/Styles/7200_Domination.png", bytes("rune"));

        List<String> deleted = store.deleteVersionsExcept(List.of("16.22.1", "16.21.1"));

        assertThat(deleted).containsExactly("16.20.1");
        assertThat(root.resolve("ddragon/cdn/16.20.1")).doesNotExist();
        assertThat(store.publishedVersions()).containsExactly("16.22.1", "16.21.1");
        assertThat(store.exists("ddragon/cdn/img/perk-images/Styles/7200_Domination.png")).isTrue();
    }

    @Test
    void unDossierVideNAAucuneVersion() {
        assertThat(store().publishedVersions()).isEmpty();
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
