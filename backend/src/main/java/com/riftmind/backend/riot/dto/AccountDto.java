package com.riftmind.backend.riot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Compte Riot renvoyé par Account-V1 ({@code /riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}} ou
 * {@code by-puuid/{puuid}}). Exemple : {@code src/test/resources/riot/account.json}.
 *
 * <p>Les champs inconnus sont ignorés : Riot peut en ajouter sans casser la lecture.
 *
 * @param puuid    identifiant stable du joueur (78 caractères), clé primaire en base
 * @param gameName partie avant le {@code #} du Riot ID. Peut changer, et Riot l'omet si le compte n'en a pas
 *                 ({@code null})
 * @param tagLine  partie après le {@code #} du Riot ID. Peut changer, et Riot l'omet si le compte n'en a pas
 *                 ({@code null})
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountDto(String puuid, String gameName, String tagLine) {
}
