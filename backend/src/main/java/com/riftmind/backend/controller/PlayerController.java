package com.riftmind.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.riftmind.backend.dto.PlayerResponse;
import com.riftmind.backend.model.Player;
import com.riftmind.backend.riot.Platform;
import com.riftmind.backend.service.LeagueService;
import com.riftmind.backend.service.PlayerService;

/** Recherche de joueurs. */
@RestController
@RequestMapping("/api/players")
public class PlayerController {

    private final PlayerService playerService;
    private final LeagueService leagueService;

    public PlayerController(PlayerService playerService, LeagueService leagueService) {
        this.playerService = playerService;
        this.leagueService = leagueService;
    }

    /**
     * Joueur par Riot ID, ex. {@code GET /api/players/euw1/Joueur%20Test/EUW} pour {@code Joueur Test#EUW}.
     *
     * <ul>
     *   <li>{@code platform} : {@code euw1} ou {@code EUW}, sans tenir compte de la casse. Une valeur inconnue donne
     *       une 400.</li>
     *   <li>{@code gameName} et {@code tagLine} : encodés dans l'URL (espaces, accents…). La casse est ignorée.</li>
     *   <li>Joueur inexistant, ou sans profil LoL sur cette plateforme : 404 {@code NOT_FOUND}.</li>
     *   <li>Rangs SoloQ et Flex dans {@code ranks}, liste vide si le joueur n'est pas classé.</li>
     * </ul>
     */
    @GetMapping("/{platform}/{gameName}/{tagLine}")
    public PlayerResponse getPlayer(@PathVariable Platform platform, @PathVariable String gameName,
            @PathVariable String tagLine) {
        Player player = playerService.getOrFetch(platform, gameName, tagLine);
        return PlayerResponse.from(player, leagueService.findByPuuid(player.getPuuid()));
    }

    /**
     * Met à jour le joueur depuis Riot (Riot ID, niveau, rangs, nouvelles parties), ex.
     * {@code POST /api/players/{puuid}/refresh}. Renvoie le joueur à jour ; le front recharge ensuite l'historique.
     *
     * <ul>
     *   <li>Mise à jour trop récente (moins de 2 min) : 429 {@code REFRESH_COOLDOWN}, avec {@code Retry-After} et
     *       {@code retryAfterSeconds}.</li>
     *   <li>PUUID inconnu : 404 {@code NOT_FOUND}.</li>
     * </ul>
     */
    @PostMapping("/{puuid}/refresh")
    public PlayerResponse refresh(@PathVariable String puuid) {
        Player player = playerService.refresh(puuid);
        return PlayerResponse.from(player, leagueService.findByPuuid(player.getPuuid()));
    }
}
