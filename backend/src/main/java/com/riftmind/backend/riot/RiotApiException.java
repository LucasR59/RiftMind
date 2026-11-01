package com.riftmind.backend.riot;

/**
 * Erreur renvoyée par l'API Riot (ou impossibilité de la joindre).
 * Les sous-classes correspondent aux cas à traiter différemment (voir docs/riot-api.md §2).
 * Utilisée telle quelle pour les erreurs inattendues (400, 415…), qui signalent un bug de notre côté.
 */
public class RiotApiException extends RuntimeException {

    /** Code HTTP renvoyé par Riot, ou 0 si Riot n'a pas pu être joint. */
    private final int riotStatus;
    /** Hôte appelé, ex. {@code euw1.api.riotgames.com}. */
    private final String host;

    public RiotApiException(int riotStatus, String host, String message) {
        this(riotStatus, host, message, null);
    }

    public RiotApiException(int riotStatus, String host, String message, Throwable cause) {
        super(message, cause);
        this.riotStatus = riotStatus;
        this.host = host;
    }

    public int riotStatus() {
        return riotStatus;
    }

    public String host() {
        return host;
    }
}
