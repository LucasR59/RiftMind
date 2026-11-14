package com.riftmind.backend.assets;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Fichiers de la copie locale, sur le disque, sous le même chemin que dans l'URL :
 * {@code {dossier}/ddragon/cdn/16.21.1/img/champion/Kalista.png}.
 *
 * <ul>
 *   <li>Aucun chemin ne peut sortir du dossier (vérifié après normalisation, en plus de la liste blanche
 *       {@link AssetPath}).</li>
 *   <li>Écriture atomique : le fichier est écrit à côté sous un nom temporaire, puis renommé. Un fichier servi est
 *       donc toujours complet, même si l'application s'arrête pendant un téléchargement.</li>
 *   <li>Une version de Data Dragon n'est publiée (listée dans {@code versions.json}) qu'avec son marqueur
 *       {@code .complete}, écrit une fois tous ses fichiers copiés.</li>
 * </ul>
 */
public class AssetStore {

    static final String COMPLETE_MARKER = ".complete";
    private static final String TEMP_PREFIX = ".download-";

    private final Path root;
    /** Versions publiées, la plus récente d'abord ; recalculées après chaque changement. */
    private volatile List<String> publishedVersions;

    public AssetStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    /** Le fichier, s'il est déjà copié. */
    public Optional<Path> find(String path) {
        Path file = resolve(path);
        return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }

    public boolean exists(String path) {
        return Files.isRegularFile(resolve(path));
    }

    public byte[] read(String path) throws IOException {
        return Files.readAllBytes(resolve(path));
    }

    /** Écrit (ou remplace) un fichier, de façon atomique. */
    public void write(String path, byte[] content) throws IOException {
        Path target = resolve(path);
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), TEMP_PREFIX, ".tmp");
        try {
            Files.write(temp, content);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    // --- Versions de Data Dragon

    public boolean isPublished(String version) {
        return publishedVersions().contains(version);
    }

    /** Publie une version : elle apparaît dans {@code versions.json}. */
    public void publish(String version) throws IOException {
        write(AssetPath.versionDirectory(version) + "/" + COMPLETE_MARKER, new byte[0]);
        publishedVersions = null;
    }

    /** Versions publiées, la plus récente d'abord. */
    public List<String> publishedVersions() {
        List<String> versions = publishedVersions;
        if (versions == null) {
            versions = versionDirectories().stream()
                    .filter(version -> Files.isRegularFile(versionDirectory(version).resolve(COMPLETE_MARKER)))
                    .sorted(AssetPath.NEWEST_FIRST)
                    .toList();
            publishedVersions = versions;
        }
        return versions;
    }

    /**
     * Supprime du disque les versions qui ne sont pas dans {@code keep}, publiées ou non.
     *
     * @return les versions supprimées
     */
    public List<String> deleteVersionsExcept(Collection<String> keep) throws IOException {
        List<String> deleted = new ArrayList<>();
        for (String version : versionDirectories()) {
            if (!keep.contains(version)) {
                deleteRecursively(versionDirectory(version));
                deleted.add(version);
            }
        }
        publishedVersions = null;
        return deleted;
    }

    /** Dossiers {@code ddragon/cdn/{version}} présents sur le disque (les autres noms sont ignorés). */
    private List<String> versionDirectories() {
        Path cdn = resolve("ddragon/cdn");
        if (!Files.isDirectory(cdn)) {
            return List.of();
        }
        List<String> versions = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(cdn, Files::isDirectory)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (AssetPath.isVersion(name)) {
                    versions.add(name);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return versions;
    }

    private Path versionDirectory(String version) {
        return resolve(AssetPath.versionDirectory(version));
    }

    /** Suppression sans suivre les liens symboliques ({@link Files#walk} ne les suit pas). */
    private void deleteRecursively(Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        }
    }

    /** Chemin sur le disque ; refuse tout ce qui sortirait du dossier. */
    Path resolve(String path) {
        Path file = root.resolve(path).normalize();
        if (!file.startsWith(root) || file.equals(root)) {
            throw new IllegalArgumentException("Chemin hors du dossier des images : " + path);
        }
        return file;
    }
}
