package com.riftmind.backend.riot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.riftmind.backend.riot.RiotProperties.RateLimit;
import com.riftmind.backend.riot.RiotProperties.Window;

/** Limiteur de requêtes par hôte (tâche 1.5). Horloge simulée : les tests ne dorment pas pour de vrai. */
class RiotRateLimiterTest {

    private static final String EUW = "euw1.api.riotgames.com";
    private static final String NA = "na1.api.riotgames.com";

    /** Horloge simulée : chaque attente est enregistrée puis fait avancer l'horloge. */
    private final AtomicLong now = new AtomicLong();
    private final List<Duration> waits = new ArrayList<>();
    /** Action déclenchée pendant la première attente (ex. des réponses qui arrivent). */
    private Runnable duringFirstWait = () -> { };

    private final RiotRateLimiter limiter = new RiotRateLimiter(RateLimit.defaults(), now::get, duration -> {
        waits.add(duration);
        if (waits.size() == 1) {
            duringFirstWait.run();
        }
        advance(duration);
    });

    private void advance(Duration duration) {
        now.addAndGet(duration.toNanos());
    }

    /** Un appel instantané : autorisation obtenue puis rendue aussitôt. */
    private void call(String host) {
        limiter.acquire(host).close();
    }

    @Test
    void la21eRequeteDansLaMemeSecondeAttend() {
        for (int i = 0; i < 20; i++) {
            call(EUW);
            advance(Duration.ofMillis(10));
        }
        assertThat(waits).isEmpty();

        // 200 ms se sont écoulées depuis la 1ʳᵉ requête : la 21ᵉ attend les 800 ms restantes
        call(EUW);
        assertThat(waits).containsExactly(Duration.ofMillis(800));
    }

    @Test
    void laFenetreGlisseEtNeRepartPasAZeroAChaqueSeconde() {
        // 20 requêtes à la fin de la 1ʳᵉ seconde…
        advance(Duration.ofMillis(900));
        for (int i = 0; i < 20; i++) {
            call(EUW);
        }
        // …la 2ᵉ seconde commence, mais Riot compterait 21 requêtes en moins d'1 s : il faut attendre 900 ms.
        // (Un seau à jetons rechargé à chaque seconde laisserait passer 20 requêtes de plus.)
        advance(Duration.ofMillis(100));
        call(EUW);

        assertThat(waits).containsExactly(Duration.ofMillis(900));
    }

    /**
     * Cas observé en direct : 20 requêtes lancées en même temps n'arrivent chez Riot qu'environ 500 ms plus tard
     * (connexion TLS). La seconde de Riot commence alors à leur arrivée. La 21ᵉ doit attendre 1 s après la
     * <b>fin</b> des 20 premières, et pas 1 s après leur départ.
     */
    @Test
    void laFenetreCommenceALaFinDesRequetesEtNonALeurDepart() {
        List<RiotRateLimiter.Permit> inFlight = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            inFlight.add(limiter.acquire(EUW));
        }
        // Les réponses arrivent 500 ms après le départ, pendant que la 21ᵉ attend
        duringFirstWait = () -> {
            advance(Duration.ofMillis(500));
            inFlight.forEach(RiotRateLimiter.Permit::close);
            advance(Duration.ofMillis(-500));
        };

        call(EUW);

        // 1ʳᵉ attente : au moins 1 s, car les requêtes sont encore en cours.
        // 2ᵉ : la fenêtre se termine 1 s après leur fin (t = 1,5 s), il reste 500 ms.
        assertThat(waits).containsExactly(Duration.ofSeconds(1), Duration.ofMillis(500));
        assertThat(Duration.ofNanos(now.get())).isEqualTo(Duration.ofMillis(1500));
    }

    @Test
    void fermerDeuxFoisUneAutorisationNeLibereQuUneFois() {
        List<RiotRateLimiter.Permit> permits = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            permits.add(limiter.acquire(EUW));
        }
        RiotRateLimiter.Permit first = permits.getFirst();
        first.close();
        first.close();

        // Une seule requête terminée : une seule nouvelle peut partir. La suivante attend la fin d'une des
        // 20 en cours, qui ne vient jamais : refus après max-wait.
        advance(Duration.ofSeconds(1));
        limiter.acquire(EUW);
        assertThatThrownBy(() -> limiter.acquire(EUW)).isInstanceOf(RiotRateLimitException.class);
    }

    @Test
    void chaqueHoteASonPropreQuota() {
        for (int i = 0; i < 20; i++) {
            call(EUW);
        }
        call(NA);
        call(RegionalRoute.EUROPE.host());

        assertThat(waits).isEmpty();
    }

    @Test
    void refuseTouteDeSuiteQuandLAttenteDepasseMaxWait() {
        // 100 requêtes en 5 s : la fenêtre de 2 min est pleine
        for (int i = 0; i < 100; i++) {
            call(EUW);
            advance(Duration.ofMillis(50));
        }
        waits.clear();

        assertThatThrownBy(() -> call(EUW))
                .isInstanceOfSatisfying(RiotRateLimitException.class, e -> {
                    assertThat(e.host()).isEqualTo(EUW);
                    assertThat(e.limitType()).isEqualTo(RiotRateLimiter.LOCAL_LIMIT_TYPE);
                    // 1ʳᵉ requête terminée à t = 0, il est t = 5 s : prochain créneau dans 1 min 55 s
                    assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(115));
                });
        assertThat(waits).as("aucune attente : l'appel est refusé immédiatement").isEmpty();

        // L'appel refusé n'a rien consommé : à t = 2 min, il passe sans attendre
        advance(Duration.ofSeconds(115));
        call(EUW);
        assertThat(waits).isEmpty();
    }

    @Test
    void apresUn429LHoteEstBloquePendantRetryAfter() {
        limiter.blockHost(EUW, Duration.ofSeconds(3));

        // Les autres hôtes ne sont pas concernés
        call(NA);
        assertThat(waits).isEmpty();

        // Blocage court (≤ max-wait de 5 s) : l'appel attend la fin du blocage
        call(EUW);
        assertThat(waits).containsExactly(Duration.ofSeconds(3));
    }

    @Test
    void unBlocageLongRefuseLesAppelsAvecLeTempsRestant() {
        limiter.blockHost(EUW, Duration.ofSeconds(30));
        advance(Duration.ofSeconds(10));

        assertThatThrownBy(() -> call(EUW))
                .isInstanceOfSatisfying(RiotRateLimitException.class,
                        e -> assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(20)));

        // Un second 429 plus court ne raccourcit pas le blocage en cours
        limiter.blockHost(EUW, Duration.ofSeconds(1));
        assertThatThrownBy(() -> call(EUW)).isInstanceOf(RiotRateLimitException.class);
    }

    /** Vraie horloge et vrais threads : jamais plus de 5 appels sur 100 ms. */
    @Test
    void respecteLaLimiteAvecDesAppelsConcurrents() {
        RiotRateLimiter realLimiter = new RiotRateLimiter(new RateLimit(
                new Window(5, Duration.ofMillis(100)), new Window(1000, Duration.ofMinutes(1)), Duration.ofSeconds(5)));
        List<Long> callTimes = Collections.synchronizedList(new ArrayList<>());

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 20; i++) {
                executor.submit(() -> {
                    try (RiotRateLimiter.Permit permit = realLimiter.acquire(EUW)) {
                        callTimes.add(System.nanoTime());
                    }
                });
            }
        }

        List<Long> sorted = callTimes.stream().sorted().toList();
        assertThat(sorted).hasSize(20);
        for (int i = 5; i < sorted.size(); i++) {
            // Le 6ᵉ appel d'une série part au moins 100 ms après le 1ᵉʳ (marge pour l'ordonnanceur de la CI)
            assertThat(Duration.ofNanos(sorted.get(i) - sorted.get(i - 5)))
                    .isGreaterThanOrEqualTo(Duration.ofMillis(80));
        }
        // 20 appels à 5 par 100 ms : au moins 300 ms au total
        assertThat(Duration.ofNanos(sorted.getLast() - sorted.getFirst()))
                .isGreaterThanOrEqualTo(Duration.ofMillis(300));
    }
}
