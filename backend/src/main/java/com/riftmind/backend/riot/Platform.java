package com.riftmind.backend.riot;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Plateforme (serveur de jeu) League of Legends et sa région de routage Match-V5.
 *
 * <p>Correspondances vérifiées en direct le 2026-11-01 (voir docs/riot-api.md §3).
 * {@code ph2} et {@code th2} ne sont pas gérées : leurs hôtes ne répondent plus (serveurs fusionnés dans {@code sg2}).
 */
public enum Platform {

    BR1("br1", "BR", "Brésil", RegionalRoute.AMERICAS),
    LA1("la1", "LAN", "Amérique latine Nord", RegionalRoute.AMERICAS),
    LA2("la2", "LAS", "Amérique latine Sud", RegionalRoute.AMERICAS),
    NA1("na1", "NA", "Amérique du Nord", RegionalRoute.AMERICAS),
    EUN1("eun1", "EUNE", "Europe Nord & Est", RegionalRoute.EUROPE),
    EUW1("euw1", "EUW", "Europe Ouest", RegionalRoute.EUROPE),
    ME1("me1", "ME", "Moyen-Orient", RegionalRoute.EUROPE),
    RU("ru", "RU", "Russie", RegionalRoute.EUROPE),
    TR1("tr1", "TR", "Turquie", RegionalRoute.EUROPE),
    JP1("jp1", "JP", "Japon", RegionalRoute.ASIA),
    KR("kr", "KR", "Corée", RegionalRoute.ASIA),
    OC1("oc1", "OCE", "Océanie", RegionalRoute.SEA),
    SG2("sg2", "SEA", "Asie du Sud-Est", RegionalRoute.SEA),
    TW2("tw2", "TW", "Taïwan", RegionalRoute.SEA),
    VN2("vn2", "VN", "Vietnam", RegionalRoute.SEA);

    private final String id;
    private final String shortName;
    private final String label;
    private final RegionalRoute regionalRoute;

    Platform(String id, String shortName, String label, RegionalRoute regionalRoute) {
        this.id = id;
        this.shortName = shortName;
        this.label = label;
        this.regionalRoute = regionalRoute;
    }

    /** Identifiant Riot utilisé dans l'URL de l'API, ex. {@code euw1}. */
    public String id() {
        return id;
    }

    /** Abréviation connue des joueurs, ex. {@code EUW}. */
    public String shortName() {
        return shortName;
    }

    /** Nom affiché, ex. {@code Europe Ouest}. */
    public String label() {
        return label;
    }

    /** Région de routage Match-V5 de cette plateforme. */
    public RegionalRoute regionalRoute() {
        return regionalRoute;
    }

    /** Région à utiliser pour Account-V1 (jamais {@code sea}). */
    public RegionalRoute accountRoute() {
        return regionalRoute.forAccountApi();
    }

    /** Hôte de l'API pour Summoner-V4 / League-V4, ex. {@code euw1.api.riotgames.com}. */
    public String host() {
        return id + ".api.riotgames.com";
    }

    /** Préfixe des identifiants de match de cette plateforme, ex. {@code EUW1_}. */
    public String matchIdPrefix() {
        return id.toUpperCase(Locale.ROOT) + "_";
    }

    /**
     * Retrouve une plateforme à partir de son identifiant ({@code euw1}) ou de son abréviation ({@code euw}),
     * sans tenir compte de la casse.
     */
    public static Optional<Platform> find(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim();
        return Arrays.stream(values())
                .filter(p -> p.id.equalsIgnoreCase(normalized) || p.shortName.equalsIgnoreCase(normalized))
                .findFirst();
    }

    /** Valeurs acceptées par {@link #find(String)}, pour les messages d'erreur : {@code br1, la1, … (ou BR, LAN, …)}. */
    public static String acceptedValues() {
        return Arrays.stream(values()).map(Platform::id).collect(Collectors.joining(", "))
                + " (ou " + Arrays.stream(values()).map(Platform::shortName).collect(Collectors.joining(", ")) + ")";
    }

    /** Comme {@link #find(String)}, mais lève une exception si la plateforme est inconnue. */
    public static Platform fromValue(String value) {
        return find(value).orElseThrow(() -> new IllegalArgumentException("Plateforme inconnue : " + value));
    }

    /** Retrouve la plateforme d'un match à partir de son identifiant, ex. {@code EUW1_7123456789}. */
    public static Optional<Platform> fromMatchId(String matchId) {
        int separator = matchId == null ? -1 : matchId.indexOf('_');
        if (separator <= 0) {
            return Optional.empty();
        }
        // Seul l'identifiant exact est accepté ici (EUW1_), pas l'abréviation (EUW_)
        String prefix = matchId.substring(0, separator);
        return Arrays.stream(values()).filter(p -> p.id.equalsIgnoreCase(prefix)).findFirst();
    }
}
