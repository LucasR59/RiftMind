package com.riftmind.backend.dto;

import java.util.List;

/**
 * Une page de l'historique d'un joueur, de la partie la plus récente à la plus ancienne.
 *
 * @param page    numéro de la page, à partir de 0
 * @param size    taille de page demandée
 * @param hasMore vrai s'il reste des parties après celle-ci (bouton « Voir plus »)
 */
public record MatchPageResponse(List<MatchSummaryResponse> items, int page, int size, boolean hasMore) {
}
