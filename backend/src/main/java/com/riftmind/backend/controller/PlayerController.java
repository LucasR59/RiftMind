package com.riftmind.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.riftmind.backend.dto.PlayerResponse;
import com.riftmind.backend.riot.Platform;
import com.riftmind.backend.service.PlayerService;

/** Recherche de joueurs. */
@RestController
@RequestMapping("/api/players")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    /**
     * Joueur par Riot ID, ex. {@code GET /api/players/euw1/Joueur%20Test/EUW} pour {@code Joueur Test#EUW}.
     *
     * <ul>
     *   <li>{@code platform} : {@code euw1} ou {@code EUW}, sans tenir compte de la casse. Une valeur inconnue donne
     *       une 400.</li>
     *   <li>{@code gameName} et {@code tagLine} : encodés dans l'URL (espaces, accents…). La casse est ignorée.</li>
     *   <li>Joueur inexistant, ou sans profil LoL sur cette plateforme : 404 {@code NOT_FOUND}.</li>
     * </ul>
     */
    @GetMapping("/{platform}/{gameName}/{tagLine}")
    public PlayerResponse getPlayer(@PathVariable Platform platform, @PathVariable String gameName,
            @PathVariable String tagLine) {
        return PlayerResponse.from(playerService.getOrFetch(platform, gameName, tagLine));
    }
}
