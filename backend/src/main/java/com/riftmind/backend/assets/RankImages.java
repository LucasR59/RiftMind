package com.riftmind.backend.assets;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Emblèmes de rang, livrés avec le backend ({@code src/main/resources/assets/ranks/}) et servis comme les images de
 * Data Dragon, sous {@code /api/assets/ranks/{palier}.png}.
 *
 * <p>Pas de téléchargement : CommunityDragon, la source habituelle, est trop peu fiable (~20 s par fichier, et
 * {@code silver.svg} en erreur 522), et le zip officiel de Riot pèse 64 Mo pour 10 images. Elles changent très rarement
 * (dernière refonte : 2023). Les fichiers sont tirés des emblèmes officiels de Riot (« Ranked Emblems Latest ») par
 * {@code scripts/RankImages.java} : marges retirées, 128 px de côté, ~20 Ko chacun.
 */
public final class RankImages {

    static final String RESOURCES = "assets/ranks/";

    private RankImages() {
    }

    /**
     * Copie les emblèmes livrés dans la copie locale, s'ils manquent ou ont changé (nouvelle version du backend).
     *
     * @return le nombre de fichiers écrits
     */
    public static int install(AssetStore store) throws IOException {
        int written = 0;
        for (String path : AssetPath.rankImages()) {
            byte[] bundled = bundled(path.substring("ranks/".length()));
            if (!store.exists(path) || !Arrays.equals(store.read(path), bundled)) {
                store.write(path, bundled);
                written++;
            }
        }
        return written;
    }

    static byte[] bundled(String file) throws IOException {
        try (InputStream in = RankImages.class.getClassLoader().getResourceAsStream(RESOURCES + file)) {
            if (in == null) {
                throw new IOException("Emblème de rang absent du backend : " + RESOURCES + file);
            }
            return in.readAllBytes();
        }
    }
}
