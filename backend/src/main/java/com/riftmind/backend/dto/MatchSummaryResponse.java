package com.riftmind.backend.dto;

import java.time.Instant;
import java.util.List;

/**
 * Une partie de l'historique d'un joueur ({@code GET /api/players/{puuid}/matches}).
 *
 * @param queueId         file de jeu ({@code queues.json} de Data Dragon), ex. 420 = SoloQ
 * @param gameVersion     version du client, ex. {@code 16.21.834.2754}
 * @param durationSeconds durée en secondes
 * @param remake          partie terminée par un remake : à afficher à part, elle ne compte pas dans les stats
 * @param player          statistiques complètes du joueur dont c'est l'historique
 * @param participants    tous les joueurs de la partie (lui compris), équipe bleue puis rouge
 */
public record MatchSummaryResponse(
        String matchId,
        int queueId,
        String gameVersion,
        Instant gameStart,
        int durationSeconds,
        boolean remake,
        ParticipantResponse player,
        List<CompactParticipantResponse> participants) {
}
