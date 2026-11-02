package com.riftmind.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.riftmind.backend.dto.ChampionStatsResponse;
import com.riftmind.backend.dto.MatchPageResponse;
import com.riftmind.backend.service.MatchQueryService;

/**
 * Parties et statistiques d'un joueur déjà connu, identifié par son PUUID (renvoyé par la recherche par Riot ID).
 * Tout est lu en base, sans appeler Riot. PUUID inconnu : 404 {@code NOT_FOUND}.
 */
@RestController
@RequestMapping("/api/players/{puuid}")
public class PlayerMatchesController {

    private final MatchQueryService matchQueryService;

    public PlayerMatchesController(MatchQueryService matchQueryService) {
        this.matchQueryService = matchQueryService;
    }

    /**
     * Historique paginé, de la partie la plus récente à la plus ancienne, ex.
     * {@code GET /api/players/{puuid}/matches?page=0&size=10&queue=420}.
     *
     * <ul>
     *   <li>{@code page} : à partir de 0 (défaut 0) ; {@code size} : 1 à 50 (défaut 10). Hors limites : 400.</li>
     *   <li>{@code queue} : file de jeu ({@code queues.json}), ex. 420 = SoloQ, 440 = Flex. Absent : toutes.</li>
     * </ul>
     */
    @GetMapping("/matches")
    public MatchPageResponse getMatches(@PathVariable String puuid,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + MatchQueryService.DEFAULT_PAGE_SIZE) int size,
            @RequestParam(required = false) Integer queue) {
        return matchQueryService.getHistory(puuid, page, size, queue);
    }

    /**
     * Statistiques par champion, hors remakes, du plus joué au moins joué, ex.
     * {@code GET /api/players/{puuid}/champions?queue=420}. {@code queue} absent : toutes les files.
     */
    @GetMapping("/champions")
    public List<ChampionStatsResponse> getChampionStats(@PathVariable String puuid,
            @RequestParam(required = false) Integer queue) {
        return matchQueryService.getChampionStats(puuid, queue);
    }
}
