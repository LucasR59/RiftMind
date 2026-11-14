package com.riftmind.backend.assets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Copie locale de Data Dragon (les emblèmes de rang, eux, sont livrés avec le backend : {@link RankImages}).
 *
 * <p>À chaque passage ({@link #sync()}, au démarrage puis toutes les heures) :
 * <ol>
 *   <li>lit la dernière version de Data Dragon ; si elle est déjà publiée, s'arrête là ;</li>
 *   <li>copie ses 4 fichiers de données, puis toutes les images qu'ils citent : champions, objets, sorts, runes
 *       (~1 100 fichiers, ~30 Mo) ;</li>
 *   <li>publie la version seulement si tout est copié. Une image qui n'existe pas chez Data Dragon est tolérée (le
 *       front affiche un emplacement vide) ; une erreur passagère ne l'est pas : la version n'est pas publiée et le
 *       passage suivant reprend là où celui-ci s'est arrêté (les fichiers déjà copiés sont gardés) ;</li>
 *   <li>supprime les anciennes versions au-delà de {@code assets.keep-versions}.</li>
 * </ol>
 * Tant qu'une nouvelle version n'est pas publiée, le site continue de servir la précédente : un patch, ou Data Dragon
 * en panne, ne casse jamais l'affichage. Les icônes de profil, trop nombreuses, sont copiées à la première demande
 * ({@link AssetService}).
 *
 * <p>Les noms lus dans les fichiers de Data Dragon passent par la liste blanche {@link AssetPath} avant de servir de
 * chemin : un fichier modifié ne peut pas faire écrire en dehors du dossier.
 */
public class AssetSync {

    private static final Logger log = LoggerFactory.getLogger(AssetSync.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AssetProperties properties;
    private final AssetStore store;
    private final AssetDownloader downloader;
    /** Un seul passage à la fois. */
    private final ReentrantLock running = new ReentrantLock();

    public AssetSync(AssetProperties properties, AssetStore store, AssetDownloader downloader) {
        this.properties = properties;
        this.store = store;
        this.downloader = downloader;
    }

    /** Résultat d'un passage. */
    public enum Result {
        /** La dernière version de Data Dragon était déjà publiée. */
        UP_TO_DATE,
        /** Une nouvelle version vient d'être publiée. */
        PUBLISHED,
        /** Copie incomplète (Data Dragon injoignable, erreur passagère) : réessayée au passage suivant. */
        INCOMPLETE,
        /** Un autre passage était en cours. */
        ALREADY_RUNNING
    }

    public Result sync() {
        if (!running.tryLock()) {
            return Result.ALREADY_RUNNING;
        }
        try {
            return syncDataDragon();
        } finally {
            running.unlock();
        }
    }

    private Result syncDataDragon() {
        String latest;
        try {
            latest = latestVersion();
        } catch (AssetDownloadException e) {
            log.warn("Data Dragon injoignable, versions gardées : {} ({})", store.publishedVersions(), e.getMessage());
            return Result.INCOMPLETE;
        }
        if (store.isPublished(latest)) {
            return Result.UP_TO_DATE;
        }

        long start = System.nanoTime();
        Map<String, JsonNode> data;
        try {
            data = copyData(latest);
        } catch (AssetDownloadException | IOException e) {
            log.warn("Copie de Data Dragon {} interrompue (données) : {}", latest, e.getMessage());
            return Result.INCOMPLETE;
        }
        List<AssetPath> images = imagesOf(latest, data).stream()
                .filter(image -> !store.exists(image.path()))
                .toList();
        Copy copy = copyAll(images);
        if (copy.failed() > 0) {
            log.warn("Copie de Data Dragon {} incomplète, nouvel essai dans {} : {}", latest,
                    properties.syncInterval(), copy);
            return Result.INCOMPLETE;
        }

        try {
            store.publish(latest);
            List<String> keep = store.publishedVersions().stream().limit(properties.keepVersions()).toList();
            List<String> deleted = store.deleteVersionsExcept(keep);
            log.info("Data Dragon {} copié et publié en {} s : {}{}", latest,
                    (System.nanoTime() - start) / 1_000_000_000, copy,
                    deleted.isEmpty() ? "" : " · anciennes versions supprimées : " + deleted);
        } catch (IOException e) {
            log.warn("Publication de Data Dragon {} impossible : {}", latest, e.getMessage());
            return Result.INCOMPLETE;
        }
        return Result.PUBLISHED;
    }

    /** Dernière version de Data Dragon (première de {@code api/versions.json}), validée. */
    String latestVersion() {
        AssetPath versions = AssetPath.parse(AssetPath.VERSIONS, properties.locale()).orElseThrow();
        JsonNode list = JSON.readTree(downloader.download(versions, properties));
        String latest = list.isArray() && !list.isEmpty() ? list.get(0).stringValue("") : "";
        if (!AssetPath.isVersion(latest)) {
            throw new AssetDownloadException("version de Data Dragon inattendue : « " + latest + " »", false);
        }
        return latest;
    }

    /** Les 4 fichiers de données de la version, copiés s'ils manquent. */
    private Map<String, JsonNode> copyData(String version) throws IOException {
        Map<String, JsonNode> data = new HashMap<>();
        for (String file : AssetPath.DATA_FILES) {
            AssetPath path = allowed(AssetPath.dataPath(version, properties.locale(), file)).orElseThrow();
            byte[] content;
            if (store.exists(path.path())) {
                content = store.read(path.path());
            } else {
                content = downloader.download(path, properties);
                store.write(path.path(), content);
            }
            data.put(file, JSON.readTree(content));
        }
        return data;
    }

    /** Images citées par les fichiers de données (noms passés par la liste blanche, sans doublon). */
    Set<AssetPath> imagesOf(String version, Map<String, JsonNode> data) {
        List<String> paths = new ArrayList<>();
        data.get("champion").path("data").forEach(champion ->
                paths.add(AssetPath.imagePath(version, "champion", champion.path("id").stringValue("") + ".png")));
        data.get("item").path("data").propertyNames().forEach(id ->
                paths.add(AssetPath.imagePath(version, "item", id + ".png")));
        data.get("summoner").path("data").forEach(spell ->
                paths.add(AssetPath.imagePath(version, "spell", spell.path("image").path("full").stringValue(""))));
        for (JsonNode tree : data.get("runesReforged")) {
            paths.add("ddragon/cdn/img/" + tree.path("icon").stringValue(""));
            for (JsonNode slot : tree.path("slots")) {
                for (JsonNode rune : slot.path("runes")) {
                    paths.add("ddragon/cdn/img/" + rune.path("icon").stringValue(""));
                }
            }
        }
        Set<AssetPath> images = new LinkedHashSet<>();
        for (String path : paths) {
            allowed(path).ifPresent(images::add);
        }
        return images;
    }

    private Optional<AssetPath> allowed(String path) {
        Optional<AssetPath> asset = AssetPath.parse(path, properties.locale());
        if (asset.isEmpty()) {
            log.warn("Nom inattendu dans Data Dragon, ignoré : « {} »", path);
        }
        return asset;
    }

    // --- Téléchargements en parallèle

    /** Bilan d'une copie. */
    record Copy(int copied, long bytes, int absent, int failed) {
        @Override
        public String toString() {
            return copied + " fichiers (" + bytes / 1024 + " Ko)"
                    + (absent > 0 ? ", " + absent + " absents chez Riot" : "")
                    + (failed > 0 ? ", " + failed + " en échec" : "");
        }
    }

    /** Télécharge et enregistre les fichiers, {@code assets.download-concurrency} à la fois. */
    private Copy copyAll(List<AssetPath> assets) {
        AtomicInteger copied = new AtomicInteger();
        AtomicLong bytes = new AtomicLong();
        AtomicInteger absent = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        try (ExecutorService executor = Executors.newFixedThreadPool(properties.downloadConcurrency(),
                Thread.ofVirtual().name("assets-sync-", 0).factory())) {
            for (AssetPath asset : assets) {
                executor.execute(() -> {
                    try {
                        byte[] content = downloader.download(asset, properties);
                        store.write(asset.path(), content);
                        copied.incrementAndGet();
                        bytes.addAndGet(content.length);
                    } catch (AssetDownloadException e) {
                        (e.notFound() ? absent : failed).incrementAndGet();
                        log.debug("{}", e.getMessage());
                    } catch (IOException | RuntimeException e) {
                        failed.incrementAndGet();
                        log.warn("Copie de {} impossible : {}", asset.path(), e.toString());
                    }
                });
            }
        } // close() attend la fin de tous les téléchargements
        return new Copy(copied.get(), bytes.get(), absent.get(), failed.get());
    }
}
