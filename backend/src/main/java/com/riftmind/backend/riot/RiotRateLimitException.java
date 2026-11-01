package com.riftmind.backend.riot;

import java.time.Duration;

/**
 * Riot répond 429 : une limite de requêtes est dépassée.
 * Il faut cesser d'appeler cet hôte pendant {@link #retryAfter()} (règle Riot, voir docs/riot-api.md §2).
 */
public class RiotRateLimitException extends RiotApiException {

    /** Délai par défaut si Riot n'indique pas de {@code Retry-After} (cas des limites de service). */
    public static final Duration DEFAULT_RETRY_AFTER = Duration.ofSeconds(1);

    private final Duration retryAfter;
    private final String limitType;

    /**
     * @param retryAfter délai indiqué par l'en-tête {@code Retry-After}, ou {@code null} s'il est absent
     * @param limitType  valeur de {@code X-Rate-Limit-Type} : {@code application}, {@code method}, {@code service},
     *                   ou {@code null} (limite appliquée par un service Riot sous-jacent)
     */
    public RiotRateLimitException(String host, Duration retryAfter, String limitType) {
        super(429, host, "Limite de requêtes Riot atteinte sur " + host
                + " (type : " + (limitType != null ? limitType : "service") + ")");
        this.retryAfter = retryAfter != null ? retryAfter : DEFAULT_RETRY_AFTER;
        this.limitType = limitType;
    }

    /** Délai à respecter avant de rappeler cet hôte (jamais {@code null}). */
    public Duration retryAfter() {
        return retryAfter;
    }

    /** Type de limite dépassée, ou {@code null} pour une limite de service. */
    public String limitType() {
        return limitType;
    }
}
