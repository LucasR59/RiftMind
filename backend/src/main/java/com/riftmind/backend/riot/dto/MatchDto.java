package com.riftmind.backend.riot.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Partie renvoyée par Match-V5 ({@code /lol/match/v5/matches/{matchId}}, sur la région de routage). Exemple :
 * {@code src/test/resources/riot/match.json} (~120 Ko).
 *
 * <p>Seuls les champs utiles sont lus. La réponse complète est gardée telle quelle en base (colonne {@code raw}) pour
 * les besoins futurs.
 *
 * @param metadata identifiant du match et PUUID des participants
 * @param info     déroulement de la partie et statistiques des joueurs
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MatchDto(MetadataDto metadata, InfoDto info) {

    /**
     * @param matchId      identifiant du match, préfixé par la plateforme, ex. {@code EUW1_7000000001}
     * @param participants PUUID des joueurs, dans l'ordre de {@link InfoDto#participants()}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MetadataDto(String matchId, List<String> participants) {
    }
}
