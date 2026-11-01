package com.riftmind.backend.riot;

/**
 * Riot répond 404 : la ressource demandée (compte, joueur, match…) n'existe pas.
 * Les services la convertissent en erreur métier plus précise si besoin (ex. joueur introuvable, tâche 2.4).
 */
public class RiotNotFoundException extends RiotApiException {

    public RiotNotFoundException(String host, String message) {
        super(404, host, message);
    }
}
