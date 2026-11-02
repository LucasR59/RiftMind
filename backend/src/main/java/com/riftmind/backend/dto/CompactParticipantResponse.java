package com.riftmind.backend.dto;

import com.riftmind.backend.model.MatchParticipant;

/**
 * Joueur d'une partie, en version courte pour la carte d'historique : qui jouait quel champion, dans quelle équipe.
 *
 * @param gameName {@code null} s'il est inconnu (bots, anciennes parties)
 * @param tagLine  {@code null} s'il est inconnu
 * @param teamId   100 (bleu) ou 200 (rouge)
 */
public record CompactParticipantResponse(
        String puuid,
        String gameName,
        String tagLine,
        int teamId,
        int championId,
        String championName) {

    public static CompactParticipantResponse from(MatchParticipant p) {
        return new CompactParticipantResponse(p.getPuuid(), p.getGameName(), p.getTagLine(), p.getTeamId(),
                p.getChampionId(), p.getChampionName());
    }
}
