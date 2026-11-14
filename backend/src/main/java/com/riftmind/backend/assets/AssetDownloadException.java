package com.riftmind.backend.assets;

/**
 * Échec du téléchargement d'un fichier de Data Dragon.
 *
 * <p>{@link #notFound()} distingue un fichier qui n'existe pas (rien à réessayer) d'une erreur passagère (serveur
 * injoignable, réponse 5xx) ou d'un contenu refusé.
 */
public class AssetDownloadException extends RuntimeException {

    private final boolean notFound;

    public AssetDownloadException(String message, boolean notFound) {
        super(message);
        this.notFound = notFound;
    }

    /** Vrai si le fichier n'existe pas chez Data Dragon (403 ou 404). */
    public boolean notFound() {
        return notFound;
    }
}
