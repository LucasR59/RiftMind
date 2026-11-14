package com.riftmind.backend.assets;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Télécharge un fichier de Data Dragon et vérifie qu'il est bien ce qu'il prétend être, avant qu'il soit enregistré
 * puis servi depuis le domaine du site.
 *
 * <ul>
 *   <li>L'adresse vient toujours de {@link AssetPath#upstreamUrl} : hôte fixé par la configuration, chemin validé par
 *       la liste blanche. Une requête du navigateur ne peut pas choisir l'hôte appelé.</li>
 *   <li>Les redirections ne sont pas suivies (client HTTP du JDK par défaut) : une redirection est une erreur.</li>
 *   <li>Taille limitée ({@link #MAX_IMAGE_BYTES}, {@link #MAX_DATA_BYTES}) : la lecture s'arrête au-delà.</li>
 *   <li>Contenu vérifié : signature PNG, JSON lisible. Le type renvoyé par le serveur est ignoré :
 *       c'est le chemin qui décide du type servi.</li>
 *   <li>Ce client n'a rien à voir avec celui de l'API Riot : la clé {@code X-Riot-Token} n'est jamais envoyée.</li>
 * </ul>
 */
public class AssetDownloader {

    /** Les images de Data Dragon font quelques dizaines de Ko. */
    static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;
    /** Plus gros fichier de données attendu : {@code item.json} (~1 Mo en français). */
    static final int MAX_DATA_BYTES = 8 * 1024 * 1024;

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient restClient;

    /** @param restClient client sans en-tête par défaut, avec des délais (voir {@code AssetConfiguration}) */
    public AssetDownloader(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * @return le contenu, déjà vérifié
     * @throws AssetDownloadException si le fichier n'existe pas ({@link AssetDownloadException#notFound()}), si le
     *                                serveur ne répond pas, ou si le contenu est refusé
     */
    public byte[] download(AssetPath asset, AssetProperties properties) {
        URI uri = URI.create(asset.upstreamUrl(properties));
        int maxBytes = asset.kind().mediaType().getSubtype().equals("json") ? MAX_DATA_BYTES : MAX_IMAGE_BYTES;
        byte[] body;
        try {
            body = restClient.get().uri(uri).exchange((request, response) -> {
                HttpStatusCode status = response.getStatusCode();
                // Data Dragon répond 403 (et non 404) pour un fichier qui n'existe pas
                if (status.value() == HttpStatus.NOT_FOUND.value() || status.value() == HttpStatus.FORBIDDEN.value()) {
                    throw new AssetDownloadException(uri + " n'existe pas (" + status.value() + ")", true);
                }
                if (!status.is2xxSuccessful()) {
                    throw new AssetDownloadException(uri + " a répondu " + status.value(), false);
                }
                long length = response.getHeaders().getContentLength();
                if (length > maxBytes) {
                    throw new AssetDownloadException(uri + " est trop gros (" + length + " octets)", false);
                }
                try (InputStream in = response.getBody()) {
                    return readAtMost(in, maxBytes, uri);
                }
            });
        } catch (ResourceAccessException e) {
            throw new AssetDownloadException(uri + " injoignable : " + e.getMessage(), false);
        }
        check(asset, body, uri);
        return body;
    }

    private static byte[] readAtMost(InputStream in, int maxBytes, URI uri) throws IOException {
        byte[] body = in.readNBytes(maxBytes + 1);
        if (body.length > maxBytes) {
            throw new AssetDownloadException(uri + " est trop gros (plus de " + maxBytes + " octets)", false);
        }
        return body;
    }

    /** Refuse un contenu qui ne correspond pas au type attendu. */
    static void check(AssetPath asset, byte[] body, URI uri) {
        String problem = switch (asset.kind()) {
            case VERSIONS, DATA -> isJson(body) ? null : "n'est pas du JSON";
            case IMAGE, PROFILE_ICON, STATIC_IMAGE, RANK -> isPng(body) ? null : "n'est pas une image PNG";
        };
        if (problem != null) {
            throw new AssetDownloadException(uri + " refusé : " + problem, false);
        }
    }

    static boolean isPng(byte[] body) {
        return body.length > PNG_SIGNATURE.length
                && Arrays.equals(body, 0, PNG_SIGNATURE.length, PNG_SIGNATURE, 0, PNG_SIGNATURE.length);
    }

    static boolean isJson(byte[] body) {
        try {
            JSON.readTree(body);
            return body.length > 0;
        } catch (JacksonException e) {
            return false;
        }
    }
}
