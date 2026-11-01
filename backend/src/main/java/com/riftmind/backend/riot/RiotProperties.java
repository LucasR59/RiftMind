package com.riftmind.backend.riot;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Configuration de l'API Riot (préfixe {@code riot.} dans application.properties).
 *
 * <p>Valeurs par défaut = clé de développement (docs/riot-api.md §1-2). Pour une clé de production :
 * <pre>
 * riot.rate-limit.short-window.requests=500
 * riot.rate-limit.short-window.period=10s
 * riot.rate-limit.long-window.requests=30000
 * riot.rate-limit.long-window.period=10m
 * </pre>
 *
 * @param apiKey          clé API Riot, lue depuis la variable d'environnement {@code RIOT_API_KEY}
 * @param rateLimit       limites de requêtes appliquées à chaque hôte Riot (plateforme ou région)
 * @param refreshCooldown délai minimum entre deux mises à jour d'un même joueur
 * @param connectTimeout  délai maximum pour se connecter à un serveur Riot
 * @param readTimeout     délai maximum d'attente de la réponse de Riot
 */
@Validated
@ConfigurationProperties("riot")
public record RiotProperties(
        @NotBlank(message = "est vide : définir la variable d'environnement RIOT_API_KEY (voir README, section « Clé API Riot »)")
        String apiKey,
        @Valid @DefaultValue RateLimit rateLimit,
        @NotNull @DefaultValue("2m") Duration refreshCooldown,
        @NotNull @DefaultValue("5s") Duration connectTimeout,
        @NotNull @DefaultValue("10s") Duration readTimeout) {

    /**
     * Limites de requêtes, appliquées séparément à chaque hôte Riot.
     * Par défaut, celles d'une clé de développement : 20 requêtes/1 s et 100 requêtes/2 min.
     */
    public record RateLimit(@Valid Window shortWindow, @Valid Window longWindow) {

        public static final Window DEFAULT_SHORT_WINDOW = new Window(20, Duration.ofSeconds(1));
        public static final Window DEFAULT_LONG_WINDOW = new Window(100, Duration.ofMinutes(2));

        public RateLimit {
            shortWindow = shortWindow != null ? shortWindow : DEFAULT_SHORT_WINDOW;
            longWindow = longWindow != null ? longWindow : DEFAULT_LONG_WINDOW;
        }
    }

    /**
     * Fenêtre de limitation : au plus {@code requests} requêtes par {@code period}.
     */
    public record Window(@Positive int requests, @NotNull Duration period) {

        @Override
        public String toString() {
            // PT2M -> 2m, PT1S -> 1s
            return requests + " req/" + (period == null ? "?" : period.toString().substring(2).toLowerCase());
        }
    }

    /** Ne jamais exposer la clé dans les logs ou les messages d'erreur. */
    @Override
    public String toString() {
        return "RiotProperties[apiKey=" + maskedApiKey() + ", rateLimit=" + rateLimit
                + ", refreshCooldown=" + refreshCooldown + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + "]";
    }

    /** Version masquée de la clé, utilisable dans les logs : {@code RGAPI-****…(42 caractères)}. */
    public String maskedApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            return "<absente>";
        }
        String prefix = apiKey.startsWith("RGAPI-") ? "RGAPI-" : "";
        return prefix + "****(" + apiKey.length() + " caractères)";
    }
}
