package com.riftmind.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.riftmind.backend.dto.MatchDetailResponse;
import com.riftmind.backend.service.MatchQueryService;

/** Parties enregistrées, lues en base sans appeler Riot. */
@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final MatchQueryService matchQueryService;

    public MatchController(MatchQueryService matchQueryService) {
        this.matchQueryService = matchQueryService;
    }

    /**
     * Détail d'une partie, ex. {@code GET /api/matches/EUW1_7000000001}. Partie inconnue (ou personnalisée, que Riot
     * interdit d'afficher) : 404 {@code NOT_FOUND}.
     */
    @GetMapping("/{matchId}")
    public MatchDetailResponse getMatch(@PathVariable String matchId) {
        return matchQueryService.getMatch(matchId);
    }
}
