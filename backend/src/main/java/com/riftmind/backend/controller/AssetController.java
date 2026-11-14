package com.riftmind.backend.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.riftmind.backend.assets.AssetPath;
import com.riftmind.backend.assets.AssetService;

/**
 * Images et données du jeu, servies depuis la copie locale ({@link AssetService}) : le navigateur ne contacte jamais
 * Data Dragon. Les chemins sont ceux de Data Dragon, ex.
 * {@code /api/assets/ddragon/cdn/16.21.1/img/champion/Kalista.png} (liste complète : {@link AssetPath}).
 *
 * <p>Performances : même domaine que le site (pas de connexion de plus à ouvrir), fichiers versionnés en cache un an
 * ({@code immutable} : jamais redemandés), les autres revalidés avec {@code Last-Modified} ou {@code ETag} (réponse
 * 304 sans contenu), JSON compressé (gzip, {@code server.compression}).
 *
 * <p>Sécurité, sur toutes les réponses :
 * <ul>
 *   <li>{@code Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; sandbox} : un fichier ouvert
 *       directement ne peut rien exécuter ni charger ;</li>
 *   <li>{@code X-Content-Type-Options: nosniff} : le navigateur s'en tient au type annoncé, fixé par le chemin ;</li>
 *   <li>{@code Cross-Origin-Resource-Policy: same-origin} : un autre site ne peut pas afficher ces images en
 *       utilisant notre bande passante.</li>
 * </ul>
 * Un chemin hors de la liste blanche, ou un fichier absent, répond 404 sans contenu.
 */
@RestController
@RequestMapping("/api/assets")
public class AssetController {

    static final String CONTENT_SECURITY_POLICY = "default-src 'none'; style-src 'unsafe-inline'; sandbox";

    private final AssetService assets;

    public AssetController(AssetService assets) {
        this.assets = assets;
    }

    @GetMapping("/{*path}")
    public ResponseEntity<?> get(@PathVariable String path) throws IOException {
        Optional<AssetPath> asset = assets.parse(path.startsWith("/") ? path.substring(1) : path);
        if (asset.isEmpty()) {
            return notFound();
        }
        AssetPath.Kind kind = asset.get().kind();
        if (kind == AssetPath.Kind.VERSIONS) {
            List<String> versions = assets.publishedVersions();
            return secured(ResponseEntity.ok())
                    .cacheControl(kind.cacheControl())
                    .eTag(DigestUtils.md5DigestAsHex(String.join(",", versions).getBytes(StandardCharsets.UTF_8)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(versions);
        }
        Optional<Path> file = assets.find(asset.get());
        if (file.isEmpty()) {
            return notFound();
        }
        return secured(ResponseEntity.ok())
                .cacheControl(kind.cacheControl())
                .lastModified(Files.getLastModifiedTime(file.get()).toInstant())
                .contentType(kind.mediaType())
                .body(new FileSystemResource(file.get()));
    }

    private static ResponseEntity<Void> notFound() {
        return secured(ResponseEntity.status(HttpStatus.NOT_FOUND)).cacheControl(CacheControl.noStore()).build();
    }

    private static ResponseEntity.BodyBuilder secured(ResponseEntity.BodyBuilder response) {
        return response
                .header("Content-Security-Policy", CONTENT_SECURITY_POLICY)
                .header("X-Content-Type-Options", "nosniff")
                .header("Cross-Origin-Resource-Policy", "same-origin");
    }
}
