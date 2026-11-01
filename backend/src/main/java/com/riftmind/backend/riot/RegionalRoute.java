package com.riftmind.backend.riot;

/**
 * Région de routage de l'API Riot ({@code <region>.api.riotgames.com}).
 * Utilisée par Account-V1 et Match-V5 (voir docs/riot-api.md §3).
 */
public enum RegionalRoute {

    AMERICAS("americas"),
    ASIA("asia"),
    EUROPE("europe"),
    SEA("sea");

    private final String id;

    RegionalRoute(String id) {
        this.id = id;
    }

    /** Identifiant utilisé dans l'URL, ex. {@code europe}. */
    public String id() {
        return id;
    }

    /** Hôte de l'API, ex. {@code europe.api.riotgames.com}. */
    public String host() {
        return id + ".api.riotgames.com";
    }

    /**
     * Région à utiliser pour Account-V1, qui n'est pas servi sur {@code sea} (réponse 403).
     * Les données de compte sont identiques sur tous les clusters : on se rabat sur {@code asia}, le plus proche.
     */
    public RegionalRoute forAccountApi() {
        return this == SEA ? ASIA : this;
    }
}
