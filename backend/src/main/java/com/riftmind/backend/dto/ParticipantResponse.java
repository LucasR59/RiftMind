package com.riftmind.backend.dto;

import java.util.Arrays;
import java.util.List;

import com.riftmind.backend.model.MatchParticipant;

/**
 * Statistiques complètes d'un joueur dans une partie : le joueur recherché dans l'historique, et les 10 joueurs du
 * détail d'une partie. Les identifiants (champion, objets, sorts, runes) sont ceux de Data Dragon.
 *
 * @param participantId  1 à 10 (16 en Arena), unique dans la partie
 * @param gameName       nom du Riot ID au moment de la partie, {@code null} s'il est inconnu (bots)
 * @param tagLine        tag du Riot ID au moment de la partie, {@code null} s'il est inconnu
 * @param teamId         100 (bleu) ou 200 (rouge)
 * @param championId     {@code key} du champion dans {@code champion.json}
 * @param championName   {@code id} du champion, ex. {@code KogMaw} : nom de l'image {@code img/champion/KogMaw.png}
 * @param position       {@code TOP}, {@code JUNGLE}, {@code MIDDLE}, {@code BOTTOM}, {@code UTILITY}, ou vide
 * @param cs             sbires et monstres tués
 * @param items          7 cases : 6 d'inventaire puis la balise, 0 = case vide
 * @param summonerSpells 2 sorts d'invocateur ({@code key} dans {@code summoner.json})
 * @param keystone       rune principale ({@code runesReforged.json}), 0 si absente
 * @param secondaryStyle arbre de runes secondaire, 0 si absent
 */
public record ParticipantResponse(
        int participantId,
        String puuid,
        String gameName,
        String tagLine,
        int teamId,
        int championId,
        String championName,
        int champLevel,
        String position,
        boolean win,
        int kills,
        int deaths,
        int assists,
        int cs,
        int gold,
        int damageChampions,
        int visionScore,
        List<Integer> items,
        List<Integer> summonerSpells,
        int keystone,
        int secondaryStyle) {

    public static ParticipantResponse from(MatchParticipant p) {
        return new ParticipantResponse(p.getParticipantId(), p.getPuuid(), p.getGameName(), p.getTagLine(),
                p.getTeamId(), p.getChampionId(), p.getChampionName(), p.getChampLevel(), p.getPosition(), p.isWin(),
                p.getKills(), p.getDeaths(), p.getAssists(), p.getCs(), p.getGold(), p.getDamageChampions(),
                p.getVisionScore(), Arrays.stream(p.getItems()).boxed().toList(),
                Arrays.stream(p.getSummonerSpells()).boxed().toList(), p.getKeystone(), p.getSecondaryStyle());
    }
}
