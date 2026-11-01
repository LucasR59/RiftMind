package com.riftmind.backend.riot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Invocateur renvoyé par Summoner-V4 ({@code /lol/summoner/v4/summoners/by-puuid/{puuid}}), propre à une plateforme.
 * Exemple : {@code src/test/resources/riot/summoner.json}.
 *
 * <p>Seuls les champs utiles sont lus : {@code revisionDate} est ignoré. Les anciens identifiants {@code id} et
 * {@code accountId} ne sont plus renvoyés par Riot. Le nom vient du Riot ID ({@link AccountDto}).
 *
 * @param puuid         identifiant stable du joueur
 * @param profileIconId icône de profil, image Data Dragon {@code img/profileicon/{id}.png}
 * @param summonerLevel niveau du compte sur cette plateforme
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SummonerDto(String puuid, int profileIconId, long summonerLevel) {
}
