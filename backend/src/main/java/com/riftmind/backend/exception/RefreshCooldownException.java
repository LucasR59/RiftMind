package com.riftmind.backend.exception;

import java.time.Duration;

/**
 * Le joueur a été mis à jour trop récemment (délai {@code riot.refresh-cooldown}) : 429 {@code REFRESH_COOLDOWN}, avec
 * le temps restant dans {@code Retry-After}.
 */
public class RefreshCooldownException extends RuntimeException {

    private final Duration retryAfter;

    /** @param retryAfter temps restant avant la prochaine mise à jour possible (au moins 1 s) */
    public RefreshCooldownException(Duration retryAfter) {
        super("Joueur mis à jour trop récemment : nouvel essai possible dans " + retryAfter.toSeconds() + " s");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }

    /** Temps restant en secondes, arrondi au-dessus, au moins 1. */
    public long retryAfterSeconds() {
        return Math.max(1, (retryAfter.toMillis() + 999) / 1000);
    }
}
