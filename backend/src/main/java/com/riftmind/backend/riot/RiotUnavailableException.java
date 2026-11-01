package com.riftmind.backend.riot;

/**
 * Riot est indisponible : erreur 5xx, ou serveur injoignable (timeout, erreur réseau).
 * À réessayer plus tard.
 */
public class RiotUnavailableException extends RiotApiException {

    /** Erreur 5xx renvoyée par Riot. */
    public RiotUnavailableException(int riotStatus, String host, String message) {
        super(riotStatus, host, message);
    }

    /** Riot n'a pas pu être joint (timeout, connexion refusée…) : {@link #riotStatus()} vaut 0. */
    public RiotUnavailableException(String host, Throwable cause) {
        super(0, host, "API Riot injoignable sur " + host + " : " + cause.getMessage(), cause);
    }
}
