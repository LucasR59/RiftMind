package com.riftmind.backend.model;

import java.time.Instant;

/**
 * Partie classée d'un joueur, réduite à ce qu'il faut pour lui attribuer un gain ou une perte de LP
 * ({@code LpChanges}).
 *
 * @param queueId         420 (SoloQ) ou 440 (Flex)
 * @param durationSeconds durée en secondes : la partie se termine à {@code gameStart + durationSeconds}
 * @param win             victoire du joueur
 * @param remake          remake : ne compte ni en victoire ni en défaite
 */
public record RankedGame(String matchId, int queueId, Instant gameStart, int durationSeconds, boolean win,
        boolean remake) {

    /** Fin de la partie. */
    public Instant gameEnd() {
        return gameStart.plusSeconds(durationSeconds);
    }
}
