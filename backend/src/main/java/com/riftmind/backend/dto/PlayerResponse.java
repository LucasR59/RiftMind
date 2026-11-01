package com.riftmind.backend.dto;

import java.time.Instant;

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
 */
public record PlayerResponse(
        String puuid,
        String gameName,
        String tagLine,
        String platform,
        int summonerLevel,
        int profileIconId,
        Instant lastRefreshedAt) {

    public static PlayerResponse from(Player player) {
        return new PlayerResponse(player.getPuuid(), player.getGameName(), player.getTagLine(),
                player.getPlatform().id(), player.getSummonerLevel(), player.getProfileIconId(),
                player.getLastRefreshedAt());
    }
}
