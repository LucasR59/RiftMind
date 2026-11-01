package com.riftmind.backend.riot;

/**
 * Riot répond 401 ou 403 : la clé API est absente, invalide, expirée (clé de dev : 24 h) ou blacklistée.
 * C'est un problème de configuration du serveur, pas de l'utilisateur.
 */
public class RiotApiKeyException extends RiotApiException {

    public RiotApiKeyException(int riotStatus, String host) {
        super(riotStatus, host, "Clé API Riot refusée (" + riotStatus + ") par " + host
                + " : clé expirée, invalide ou blacklistée. Renouveler RIOT_API_KEY (voir README, « Clé API Riot »)");
    }
}
