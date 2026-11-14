package com.riftmind.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.riftmind.backend.dto.RankHistoryResponse;
import com.riftmind.backend.service.RankHistoryService;

/** Évolution du rang d'un joueur déjà connu, et son peak (tâche 7.10). Lu en base, sans appeler Riot. */
@RestController
public class RankHistoryController {

    private final RankHistoryService rankHistoryService;

    public RankHistoryController(RankHistoryService rankHistoryService) {
        this.rankHistoryService = rankHistoryService;
    }

    /**
     * {@code GET /api/players/{puuid}/rank-history} : une entrée par file classée (SoloQ puis Flex) ayant au moins un
     * rang enregistré, avec ses points et son peak. PUUID inconnu : 404 {@code NOT_FOUND}.
     */
    @GetMapping("/api/players/{puuid}/rank-history")
    public List<RankHistoryResponse> getRankHistory(@PathVariable String puuid) {
        return rankHistoryService.getHistory(puuid);
    }
}
