package com.riftmind.backend.dto;

import java.time.Instant;
import java.util.List;

import com.riftmind.backend.model.LeagueEntry;
import com.riftmind.backend.model.Player;

/**
 * Joueur renvoyé au front par {@code GET /api/players/{platform}/{gameName}/{tagLine}}.
 *
 * @param puuid           identifiant stable du joueur, pour les appels suivants (matchs, stats…)
 * @param gameName        nom du Riot ID, avec l'écriture exacte de Riot
 * @param tagLine         tag du Riot ID, sans le {@code #}
 * @param platform        identifiant Riot de la plateforme, en minuscules ({@code euw1}), comme dans les URL du front
 * @param summonerLevel   niveau du compte
 * @param profileIconId   icône Data Dragon : {@code img/profileicon/{id}.png}
 * @param lastRefreshedAt dernière mise à jour des données depuis Riot
 * @param ranks           rangs SoloQ puis Flex, uniquement pour les files où le joueur est classé. Liste vide (jamais
 *                        {@code null}) si le joueur n'est pas classé
 */
public record PlayerResponse(
        String puuid,
        String gameName,
        String tagLine,
        String platform,
        int summonerLevel,
        int profileIconId,
        Instant lastRefreshedAt,
        List<LeagueEntryResponse> ranks) {

    /** @param ranks rangs du joueur, déjà dans l'ordre d'affichage (voir {@code LeagueService#findByPuuid}) */
    public static PlayerResponse from(Player player, List<LeagueEntry> ranks) {
        return new PlayerResponse(player.getPuuid(), player.getGameName(), player.getTagLine(),
                player.getPlatform().id(), player.getSummonerLevel(), player.getProfileIconId(),
                player.getLastRefreshedAt(), ranks.stream().map(LeagueEntryResponse::from).toList());
    }
}
