package com.riftmind.backend.assets;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fichiers servis sous {@code /api/assets/} : toujours lus depuis la copie locale.
 *
 * <p>Seule exception : une icône de profil absente du disque est téléchargée une fois chez Data Dragon, puis gardée.
 * Pour qu'une requête du navigateur ne puisse pas faire travailler Data Dragon à volonté :
 * <ul>
 *   <li>seulement pour une version publiée, et un numéro d'icône (liste blanche {@link AssetPath}) ;</li>
 *   <li>une seule requête vers Data Dragon par icône, même si 50 navigateurs la demandent en même temps ;</li>
 *   <li>au plus {@link #MAX_UPSTREAM_REQUESTS} téléchargements simultanés ; au-delà, réponse 404 immédiate (le front
 *       affiche un emplacement vide) ;</li>
 *   <li>une icône inexistante n'est pas redemandée avant {@link #MISSING_TTL}.</li>
 * </ul>
 */
public class AssetService {

    private static final Logger log = LoggerFactory.getLogger(AssetService.class);

    static final int MAX_UPSTREAM_REQUESTS = 4;
    static final Duration MISSING_TTL = Duration.ofHours(1);
    /** Taille maximale de la liste des icônes inexistantes (vidée au-delà). */
    static final int MAX_MISSING_ENTRIES = 10_000;
    private static final Duration UPSTREAM_WAIT = Duration.ofSeconds(2);

    private final AssetProperties properties;
    private final AssetStore store;
    private final AssetDownloader downloader;
    private final Clock clock;

    private final Semaphore upstream = new Semaphore(MAX_UPSTREAM_REQUESTS);
    private final Map<String, CompletableFuture<Boolean>> inFlight = new ConcurrentHashMap<>();
    private final Map<String, Instant> missingUntil = new ConcurrentHashMap<>();

    public AssetService(AssetProperties properties, AssetStore store, AssetDownloader downloader, Clock clock) {
        this.properties = properties;
        this.store = store;
        this.downloader = downloader;
        this.clock = clock;
    }

    /** Chemin reconnu par la liste blanche, ou vide. */
    public Optional<AssetPath> parse(String path) {
        return AssetPath.parse(path, properties.locale());
    }

    /** Versions de Data Dragon publiées, la plus récente d'abord (contenu de {@code versions.json}). */
    public List<String> publishedVersions() {
        return store.publishedVersions();
    }

    /** Le fichier sur le disque, téléchargé d'abord s'il s'agit d'une icône de profil pas encore copiée. */
    public Optional<Path> find(AssetPath asset) {
        Optional<Path> file = store.find(asset.path());
        if (file.isPresent() || asset.kind() != AssetPath.Kind.PROFILE_ICON
                || !store.isPublished(asset.version())) {
            return file;
        }
        return fetchOnce(asset) ? store.find(asset.path()) : Optional.empty();
    }

    /** Télécharge l'icône, une seule fois même si plusieurs requêtes la demandent en même temps. */
    private boolean fetchOnce(AssetPath asset) {
        Instant until = missingUntil.get(asset.path());
        if (until != null && clock.instant().isBefore(until)) {
            return false;
        }
        CompletableFuture<Boolean> mine = new CompletableFuture<>();
        CompletableFuture<Boolean> current = inFlight.putIfAbsent(asset.path(), mine);
        if (current != null) {
            return current.join();
        }
        try {
            boolean fetched = fetch(asset);
            mine.complete(fetched);
            return fetched;
        } finally {
            mine.complete(false); // sans effet si déjà terminé ; débloque les autres requêtes en cas d'exception
            inFlight.remove(asset.path(), mine);
        }
    }

    private boolean fetch(AssetPath asset) {
        try {
            if (!upstream.tryAcquire(UPSTREAM_WAIT.toMillis(), TimeUnit.MILLISECONDS)) {
                log.debug("Trop de téléchargements en cours, {} refusé", asset.path());
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        try {
            store.write(asset.path(), downloader.download(asset, properties));
            return true;
        } catch (AssetDownloadException e) {
            if (e.notFound()) {
                rememberMissing(asset.path());
            }
            log.debug("{}", e.getMessage());
            return false;
        } catch (IOException e) {
            log.warn("Enregistrement de {} impossible : {}", asset.path(), e.toString());
            return false;
        } finally {
            upstream.release();
        }
    }

    private void rememberMissing(String path) {
        if (missingUntil.size() >= MAX_MISSING_ENTRIES) {
            missingUntil.clear();
        }
        missingUntil.put(path, clock.instant().plus(MISSING_TTL));
    }
}
