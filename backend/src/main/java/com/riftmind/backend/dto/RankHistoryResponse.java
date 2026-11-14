package com.riftmind.backend.dto;

import java.util.List;

/**
 * Évolution du rang d'un joueur dans une file classée, depuis que RiftMind le suit.
 *
 * @param queueType {@code RANKED_SOLO_5x5} (SoloQ) ou {@code RANKED_FLEX_SR} (Flex)
 * @param points    rangs successifs, du plus ancien au plus récent (un point par changement de rang ou de bilan ;
 *                  au plus {@link com.riftmind.backend.service.RankHistoryService#MAX_POINTS}, les plus récents)
 * @param peak      meilleur rang enregistré (le premier atteint en cas d'égalité), sur tout l'historique
 */
public record RankHistoryResponse(
        String queueType,
        List<RankPointResponse> points,
        RankPointResponse peak) {
}
