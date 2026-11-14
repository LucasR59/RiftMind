package com.riftmind.backend.assets;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Emblèmes de rang livrés avec le backend (src/main/resources/assets/ranks/). */
class RankImagesTest {

    @TempDir
    Path root;

    @Test
    void lesOnzeEmblemesSontLivresEnPngCarresEtLegers() throws Exception {
        for (String path : AssetPath.rankImages()) {
            byte[] png = RankImages.bundled(path.substring("ranks/".length()));

            assertThat(AssetDownloader.isPng(png)).as(path).isTrue();
            assertThat(png.length).as(path).isLessThan(40 * 1024);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            assertThat(image.getWidth()).as(path).isEqualTo(128);
            assertThat(image.getHeight()).as(path).isEqualTo(128);
            assertThat(image.getColorModel().hasAlpha()).as(path).isTrue();
        }
    }

    @Test
    void sontInstallesDansLaCopieLocaleUneSeuleFois() throws Exception {
        AssetStore store = new AssetStore(root);

        assertThat(RankImages.install(store)).isEqualTo(11);
        assertThat(RankImages.install(store)).isZero();

        assertThat(store.read("ranks/silver.png")).isEqualTo(RankImages.bundled("silver.png"));
        assertThat(store.exists("ranks/unranked.png")).isTrue();
    }

    @Test
    void unFichierModifieOuAncienEstRemplace() throws Exception {
        AssetStore store = new AssetStore(root);
        RankImages.install(store);
        store.write("ranks/gold.png", new byte[] {1, 2, 3});

        assertThat(RankImages.install(store)).isEqualTo(1);

        assertThat(store.read("ranks/gold.png")).isEqualTo(RankImages.bundled("gold.png"));
    }
}
