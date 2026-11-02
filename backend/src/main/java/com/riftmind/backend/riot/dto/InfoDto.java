package com.riftmind.backend.riot.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Partie {@code info} d'un {@link MatchDto}. Seuls les champs utiles sont lus.
 *
 * @param gameCreation       création du salon (avant la sélection des champions), en ms depuis l'epoch
 * @param gameStartTimestamp début de la partie, en ms depuis l'epoch
 * @param gameEndTimestamp   fin de la partie, en ms depuis l'epoch. Absent ({@code null}) avant le patch 11.20
 * @param gameDuration       durée : en secondes si {@code gameEndTimestamp} est présent, en millisecondes sinon
 *                           (voir {@link #durationSeconds()})
 * @param gameVersion        version du client, ex. {@code 16.21.834.2754} (patch {@code 16.21})
 * @param queueId            file de jeu ({@code queues.json} de Data Dragon), ex. 420 = SoloQ, 0 = partie personnalisée
 * @param platformId         plateforme du match, ex. {@code EUW1}
 * @param gameMode           mode, ex. {@code CLASSIC}, {@code ARAM}, {@code CHERRY} (Arena)
 * @param mapId              carte, ex. 11 = Faille de l'invocateur
 * @param participants       statistiques des joueurs (10, ou 16 en Arena)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InfoDto(
        long gameCreation,
        long gameStartTimestamp,
        Long gameEndTimestamp,
        long gameDuration,
        String gameVersion,
        int queueId,
        String platformId,
        String gameMode,
        int mapId,
        List<ParticipantDto> participants) {

    /** Durée de la partie en secondes, quel que soit le format de Riot (ms avant le patch 11.20). */
    public long durationSeconds() {
        return gameEndTimestamp != null ? gameDuration : gameDuration / 1000;
    }

    /**
     * Partie terminée par un remake (vote de reddition anticipée après une déconnexion, avant 3-4 min de jeu) : Riot
     * marque alors chaque participant avec {@code gameEndedInEarlySurrender}. Un remake reste dans l'historique, mais
     * ne compte pas dans les statistiques (winrate, KDA…).
     */
    public boolean isRemake() {
        return participants != null && participants.stream().anyMatch(ParticipantDto::gameEndedInEarlySurrender);
    }

    /** Début de la partie. */
    public Instant gameStart() {
        return Instant.ofEpochMilli(gameStartTimestamp);
    }
}
