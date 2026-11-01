package com.riftmind.backend.riot;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Limiteur de requêtes vers l'API Riot, <b>par hôte</b> : chaque plateforme ({@code euw1.api…}) et chaque région
 * ({@code europe.api…}) a son propre quota chez Riot (docs/riot-api.md §2).
 *
 * <h2>Règle appliquée</h2>
 * Pour chaque fenêtre (ex. 20 req/1 s), une nouvelle requête ne part que si la 20ᵉ requête précédente s'est
 * <b>terminée</b> depuis au moins 1 s. Les requêtes encore en cours comptent comme « pas encore terminées ».
 * On compte à partir de la fin des requêtes, et non de leur départ, parce que Riot les a forcément reçues avant
 * qu'elles se terminent. Le délai réseau ne peut donc pas tasser 21 requêtes dans la même seconde côté Riot.
 * En pratique, au premier appel, la connexion TLS retarde l'arrivée chez Riot d'environ 500 ms. Compter à partir
 * du départ provoquait des 429 : vérifié en direct, voir docs/riot-api.md §2.
 *
 * <p>C'est une fenêtre glissante : au plus N requêtes sur <i>n'importe quel</i> intervalle de la période.
 * Un seau à jetons (Bucket4j par exemple) ne garantit pas cela : à cheval sur deux intervalles, il laisse passer
 * jusqu'à 2N requêtes.
 *
 * <h2>Utilisation</h2>
 * <pre>
 * try (RiotRateLimiter.Permit permit = limiter.acquire(host)) {
 *     // appel HTTP
 * }
 * </pre>
 * <ul>
 *   <li>Si le quota est épuisé, {@link #acquire} attend (la 21ᵉ requête de la seconde attend).</li>
 *   <li>Si l'attente dépasse {@code riot.rate-limit.max-wait}, l'appel échoue tout de suite avec
 *       {@link RiotRateLimitException}. Le front reçoit un 503 avec {@code Retry-After} au lieu d'attendre jusqu'à
 *       2 minutes.</li>
 *   <li>Après un 429 de Riot, {@link #blockHost} bloque tous les appels vers l'hôte pendant {@code Retry-After}.</li>
 * </ul>
 */
public class RiotRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RiotRateLimiter.class);

    /** Valeur de {@link RiotRateLimitException#limitType()} quand c'est notre propre limiteur qui refuse l'appel. */
    public static final String LOCAL_LIMIT_TYPE = "riftmind";

    /** Autorisation d'appeler un hôte. À fermer dès que la réponse est reçue, ou en cas d'erreur. */
    public interface Permit extends AutoCloseable {
        @Override
        void close();
    }

    /** Attente d'un appel, interchangeable dans les tests pour ne pas dormir pour de vrai. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final List<RiotProperties.Window> windows;
    private final long maxWaitNanos;
    private final LongSupplier nanoClock;
    private final Sleeper sleeper;
    private final Map<String, HostState> hosts = new ConcurrentHashMap<>();

    public RiotRateLimiter(RiotProperties.RateLimit config) {
        this(config, System::nanoTime, Thread::sleep);
    }

    RiotRateLimiter(RiotProperties.RateLimit config, LongSupplier nanoClock, Sleeper sleeper) {
        this.windows = List.of(config.shortWindow(), config.longWindow());
        this.maxWaitNanos = config.maxWait().toNanos();
        this.nanoClock = nanoClock;
        this.sleeper = sleeper;
    }

    /**
     * Attend que le quota de {@code host} permette un appel.
     *
     * @return l'autorisation, à fermer quand la réponse est reçue
     * @throws RiotRateLimitException si l'attente totale dépasserait {@code max-wait}
     */
    public Permit acquire(String host) {
        HostState state = hosts.computeIfAbsent(host, h -> new HostState(windows));
        long start = nanoClock.getAsLong();
        while (true) {
            long now = nanoClock.getAsLong();
            // Attente minimale avant de pouvoir appeler. Elle peut s'allonger si une requête en cours se
            // termine tard : on revérifie donc après chaque attente.
            long wait = state.tryAcquire(now);
            if (wait == 0) {
                AtomicBoolean closed = new AtomicBoolean();
                return () -> {
                    if (closed.compareAndSet(false, true)) { // fermer deux fois ne compte qu'une fois
                        state.release(nanoClock);
                    }
                };
            }
            if (now - start + wait > maxWaitNanos) {
                Duration retryAfter = Duration.ofNanos(wait);
                log.debug("Limite locale atteinte pour {} : appel refusé, prochain créneau dans {} ms",
                        host, retryAfter.toMillis());
                throw new RiotRateLimitException(host, retryAfter, LOCAL_LIMIT_TYPE);
            }
            log.debug("Limite locale pour {} : attente de {} ms avant l'appel", host, wait / 1_000_000);
            try {
                sleeper.sleep(Duration.ofNanos(wait));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RiotUnavailableException(host, e);
            }
        }
    }

    /** Riot a répondu 429 : plus aucun appel vers {@code host} pendant {@code retryAfter}. */
    public void blockHost(String host, Duration retryAfter) {
        hosts.computeIfAbsent(host, h -> new HostState(windows))
                .blockUntil(nanoClock.getAsLong() + retryAfter.toNanos());
    }

    /** Quota d'un hôte. Les méthodes sont synchronisées, mais l'attente se fait en dehors du verrou. */
    private static final class HostState {

        private final SlidingWindow[] windows;
        private long blockedUntil = Long.MIN_VALUE;
        private int inFlight;

        HostState(List<RiotProperties.Window> config) {
            this.windows = config.stream().map(SlidingWindow::new).toArray(SlidingWindow[]::new);
        }

        /** @return 0 si l'appel peut partir (il est alors compté « en cours »), sinon l'attente minimale (> 0) */
        synchronized long tryAcquire(long now) {
            long slot = Math.max(now, blockedUntil);
            for (SlidingWindow window : windows) {
                slot = Math.max(slot, window.earliestSlot(now, inFlight));
            }
            if (slot <= now) {
                inFlight++;
                return 0;
            }
            return slot - now;
        }

        /** L'heure est lue sous le verrou, pour que les fins restent triées dans {@link SlidingWindow}. */
        synchronized void release(LongSupplier clock) {
            long now = clock.getAsLong();
            inFlight--;
            for (SlidingWindow window : windows) {
                window.recordEnd(now);
            }
        }

        synchronized void blockUntil(long until) {
            blockedUntil = Math.max(blockedUntil, until);
        }
    }

    /**
     * Fins des N dernières requêtes terminées (en nanosecondes), dans un tableau circulaire.
     * L'horloge étant monotone, le tableau est trié par construction.
     */
    private static final class SlidingWindow {

        private final long[] ends;
        private final long periodNanos;
        private int next;
        private int count;

        SlidingWindow(RiotProperties.Window config) {
            this.ends = new long[config.requests()];
            this.periodNanos = config.period().toNanos();
        }

        /**
         * Premier instant où une nouvelle requête respecte la fenêtre : fin de la N-ième requête la plus récente,
         * plus une période. Les requêtes en cours sont les plus récentes, et leur fin est au plus tôt maintenant.
         */
        long earliestSlot(long now, int inFlight) {
            int rank = ends.length - inFlight; // rang de la N-ième requête parmi celles déjà terminées
            if (rank <= 0) {
                return now + periodNanos;
            }
            if (count < rank) {
                return Long.MIN_VALUE; // moins de N requêtes au total
            }
            return ends[Math.floorMod(next - rank, ends.length)] + periodNanos;
        }

        void recordEnd(long end) {
            ends[next] = end;
            next = (next + 1) % ends.length;
            count = Math.min(count + 1, ends.length);
        }
    }
}
