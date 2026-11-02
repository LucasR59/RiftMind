package com.riftmind.backend.dto;

import java.util.List;

/**
 * Totaux d'une équipe dans le détail d'une partie, calculés à partir de ses joueurs.
 *
 * @param teamId 100 (bleu) ou 200 (rouge)
 */
public record TeamResponse(int teamId, boolean win, int kills, int deaths, int assists, int gold,
        int damageChampions) {

    /** Totaux des joueurs d'une équipe ({@code players} non vide, tous de la même équipe). */
    static TeamResponse of(int teamId, List<ParticipantResponse> players) {
        return new TeamResponse(teamId,
                players.stream().anyMatch(ParticipantResponse::win),
                players.stream().mapToInt(ParticipantResponse::kills).sum(),
                players.stream().mapToInt(ParticipantResponse::deaths).sum(),
                players.stream().mapToInt(ParticipantResponse::assists).sum(),
                players.stream().mapToInt(ParticipantResponse::gold).sum(),
                players.stream().mapToInt(ParticipantResponse::damageChampions).sum());
    }
}
