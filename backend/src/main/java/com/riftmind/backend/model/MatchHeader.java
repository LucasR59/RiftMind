package com.riftmind.backend.model;

import java.time.Instant;

/**
 * En-tête d'une partie : les colonnes de {@link Match} sans la réponse brute ({@code raw}, ~120 Ko). À utiliser pour
 * afficher des parties (historique, détail).
 *
 * @param durationSeconds durée en secondes
 * @param remake          partie terminée par un remake, à exclure des statistiques
 */
public record MatchHeader(String matchId, String platform, int queueId, String gameVersion, Instant gameStart,
        int durationSeconds, boolean remake) {
}
