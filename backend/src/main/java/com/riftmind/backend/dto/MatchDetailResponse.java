package com.riftmind.backend.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.riftmind.backend.model.MatchHeader;

/**
 * Détail complet d'une partie ({@code GET /api/matches/{matchId}}) : tous les joueurs et les totaux par équipe.
 *
 * @param platform        plateforme du match, ex. {@code EUW1}
 * @param queueId         file de jeu ({@code queues.json} de Data Dragon)
 * @param durationSeconds durée en secondes
 * @param remake          partie terminée par un remake
 * @param teams           totaux par équipe, bleue (100) puis rouge (200)
 * @param participants    tous les joueurs, équipe bleue puis rouge
 */
public record MatchDetailResponse(
        String matchId,
        String platform,
        int queueId,
        String gameVersion,
        Instant gameStart,
        int durationSeconds,
        boolean remake,
        List<TeamResponse> teams,
        List<ParticipantResponse> participants) {

    public static MatchDetailResponse of(MatchHeader header, List<ParticipantResponse> participants) {
        Map<Integer, List<ParticipantResponse>> byTeam = participants.stream()
                .collect(Collectors.groupingBy(ParticipantResponse::teamId, TreeMap::new, Collectors.toList()));
        List<TeamResponse> teams = byTeam.entrySet().stream()
                .map(team -> TeamResponse.of(team.getKey(), team.getValue()))
                .toList();
        return new MatchDetailResponse(header.matchId(), header.platform(), header.queueId(), header.gameVersion(),
                header.gameStart(), header.durationSeconds(), header.remake(), teams, participants);
    }
}
