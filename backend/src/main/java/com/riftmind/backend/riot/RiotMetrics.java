package com.riftmind.backend.riot;

import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Mesures des appels à l'API Riot (tâche 8.2), visibles sur {@code /actuator/metrics} (profil dev) :
 * <ul>
 *   <li>{@code riot.api.calls} : réponses de Riot, par {@code host} et {@code status} (code HTTP, ou {@code IO_ERROR}
 *       sans réponse) ;</li>
 *   <li>{@code riot.rate.limited} : limites atteintes, par {@code host}, {@code source} ({@code riot} : réponse 429 ;
 *       {@code local} : appel refusé par notre limiteur avant d'atteindre Riot) et {@code type} ;</li>
 *   <li>{@code riot.limiter.wait} : temps d'attente imposé par notre limiteur avant un appel, par {@code host}.</li>
 * </ul>
 * Exemple : {@code /actuator/metrics/riot.rate.limited?tag=source:riot}.
 *
 * <p>Un résumé lisible est aussi écrit dans les logs à intervalle régulier ({@link #summaryAndReset()}).
 */
public class RiotMetrics {

    static final String CALLS = "riot.api.calls";
    static final String RATE_LIMITED = "riot.rate.limited";
    static final String LIMITER_WAIT = "riot.limiter.wait";

    private final MeterRegistry registry;

    /** Compteurs depuis le dernier résumé, par hôte. */
    private final Map<String, LongAdder> callsSinceSummary = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> riotLimitsSinceSummary = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> localLimitsSinceSummary = new ConcurrentHashMap<>();

    public RiotMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Mesures en mémoire seulement : pour les tests et les clients créés à la main. */
    public static RiotMetrics inMemory() {
        return new RiotMetrics(new SimpleMeterRegistry());
    }

    /** Riot a répondu (quel que soit le code). */
    void recordResponse(String host, int status) {
        record(host, String.valueOf(status));
    }

    /** Riot n'a pas répondu (timeout, connexion refusée…). */
    void recordNoResponse(String host) {
        record(host, "IO_ERROR");
    }

    private void record(String host, String status) {
        Counter.builder(CALLS)
                .description("Réponses de l'API Riot")
                .tag("host", host)
                .tag("status", status)
                .register(registry)
                .increment();
        callsSinceSummary.computeIfAbsent(host, h -> new LongAdder()).increment();
    }

    /** Riot a répondu 429. */
    void recordRiotRateLimit(String host, String limitType) {
        rateLimited(host, "riot", limitType);
        riotLimitsSinceSummary.computeIfAbsent(host, h -> new LongAdder()).increment();
    }

    /** Notre limiteur a refusé un appel (attente trop longue) : il n'est jamais parti vers Riot. */
    void recordLocalRateLimit(String host) {
        rateLimited(host, "local", RiotRateLimiter.LOCAL_LIMIT_TYPE);
        localLimitsSinceSummary.computeIfAbsent(host, h -> new LongAdder()).increment();
    }

    private void rateLimited(String host, String source, String type) {
        Counter.builder(RATE_LIMITED)
                .description("Limites de requêtes atteintes (429 de Riot ou refus du limiteur local)")
                .tag("host", host)
                .tag("source", source)
                .tag("type", type == null ? "inconnu" : type)
                .register(registry)
                .increment();
    }

    /** Temps passé à attendre notre limiteur avant un appel. */
    void recordLimiterWait(String host, Duration wait) {
        Timer.builder(LIMITER_WAIT)
                .description("Attente imposée par le limiteur de requêtes avant un appel à Riot")
                .tag("host", host)
                .register(registry)
                .record(wait);
    }

    /**
     * Résumé des appels depuis le précédent, puis remise à zéro, ex. :
     * {@code 42 appels (europe.api.riotgames.com : 30, euw1.api.riotgames.com : 12) · limites Riot (429) : 1
     * (euw1.api.riotgames.com : 1) · refus du limiteur local : 0}. {@code null} s'il n'y a eu aucun appel.
     */
    public String summaryAndReset() {
        Map<String, Long> calls = drain(callsSinceSummary);
        Map<String, Long> riotLimits = drain(riotLimitsSinceSummary);
        Map<String, Long> localLimits = drain(localLimitsSinceSummary);
        if (calls.isEmpty() && localLimits.isEmpty()) {
            return null;
        }
        return describe(calls, "appel", "appels") + " · limites Riot (429) : " + describe(riotLimits)
                + " · refus du limiteur local : " + describe(localLimits);
    }

    private static Map<String, Long> drain(Map<String, LongAdder> counters) {
        Map<String, Long> values = new TreeMap<>();
        counters.forEach((host, adder) -> {
            long value = adder.sumThenReset();
            if (value > 0) {
                values.put(host, value);
            }
        });
        return values;
    }

    private static String describe(Map<String, Long> byHost) {
        return describe(byHost, "", "");
    }

    private static String describe(Map<String, Long> byHost, String singular, String plural) {
        long total = byHost.values().stream().mapToLong(Long::longValue).sum();
        String unit = singular.isEmpty() ? "" : " " + (total > 1 ? plural : singular);
        if (total == 0) {
            return "0" + unit;
        }
        return total + unit + " (" + byHost.entrySet().stream()
                .map(e -> e.getKey() + " : " + e.getValue())
                .collect(Collectors.joining(", ")) + ")";
    }
}
