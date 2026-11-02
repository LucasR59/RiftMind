package com.riftmind.backend.riot.dto;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Statistiques d'un joueur dans une partie ({@code info.participants} de Match-V5). Riot renvoie ~150 champs : seuls
 * ceux affichés par RiftMind sont lus. Les Augments d'Arena ({@code playerAugment1…6}) ne sont volontairement pas lus
 * (interdits d'affichage par Riot, voir docs/riot-api.md).
 *
 * @param participantId                1 à 10 (16 en Arena), unique dans la partie. Le PUUID ne l'est pas : les bots
 *                                     des parties contre l'IA partagent le même
 * @param puuid                        identifiant stable du joueur
 * @param riotIdGameName               nom du Riot ID au moment de la partie
 * @param riotIdTagline                tag du Riot ID au moment de la partie
 * @param teamId                       100 (bleu) ou 200 (rouge)
 * @param championId                   identifiant numérique du champion ({@code key} dans Data Dragon)
 * @param championName                 identifiant texte du champion ({@code id} dans Data Dragon), ex. {@code KogMaw}
 * @param champLevel                   niveau du champion en fin de partie
 * @param teamPosition                 rôle : {@code TOP}, {@code JUNGLE}, {@code MIDDLE}, {@code BOTTOM},
 *                                     {@code UTILITY}, ou vide hors Faille classique
 * @param win                          victoire de l'équipe du joueur
 * @param kills                        éliminations
 * @param deaths                       morts
 * @param assists                      assistances
 * @param totalMinionsKilled           sbires tués
 * @param neutralMinionsKilled         monstres de la jungle tués (comptent dans le CS)
 * @param goldEarned                   or gagné
 * @param totalDamageDealtToChampions  dégâts infligés aux champions
 * @param visionScore                  score de vision
 * @param item0                        objets 0 à 5 de l'inventaire, 0 si la case est vide
 * @param item6                        balise (trinket)
 * @param summoner1Id                  premier sort d'invocateur ({@code key} dans {@code summoner.json})
 * @param summoner2Id                  second sort d'invocateur
 * @param perks                        runes
 * @param gameEndedInEarlySurrender    partie terminée par un remake
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ParticipantDto(
        int participantId,
        String puuid,
        String riotIdGameName,
        String riotIdTagline,
        int teamId,
        int championId,
        String championName,
        int champLevel,
        String teamPosition,
        boolean win,
        int kills,
        int deaths,
        int assists,
        int totalMinionsKilled,
        int neutralMinionsKilled,
        int goldEarned,
        int totalDamageDealtToChampions,
        int visionScore,
        int item0,
        int item1,
        int item2,
        int item3,
        int item4,
        int item5,
        int item6,
        int summoner1Id,
        int summoner2Id,
        PerksDto perks,
        boolean gameEndedInEarlySurrender) {

    /** CS : sbires et monstres de la jungle. */
    public int cs() {
        return totalMinionsKilled + neutralMinionsKilled;
    }

    /** Les 7 cases d'objets, dans l'ordre : 6 cases d'inventaire puis la balise. 0 = case vide. */
    public int[] items() {
        return new int[] {item0, item1, item2, item3, item4, item5, item6};
    }

    /** Les 2 sorts d'invocateur. */
    public int[] summonerSpells() {
        return new int[] {summoner1Id, summoner2Id};
    }

    /** Rune principale (keystone), ex. 8005 = Attaque soutenue. 0 si absente (certains modes de jeu). */
    public int keystone() {
        return style(PerkStyleDto.PRIMARY)
                .map(style -> style.selections() == null || style.selections().isEmpty()
                        ? 0 : style.selections().getFirst().perk())
                .orElse(0);
    }

    /** Arbre de runes secondaire, ex. 8200 = Sorcellerie. 0 si absent. */
    public int secondaryStyle() {
        return style(PerkStyleDto.SECONDARY).map(PerkStyleDto::style).orElse(0);
    }

    private Optional<PerkStyleDto> style(String description) {
        if (perks == null || perks.styles() == null) {
            return Optional.empty();
        }
        return perks.styles().stream().filter(s -> description.equals(s.description())).findFirst();
    }

    /** Runes d'un joueur. Les fragments de statistiques ({@code statPerks}) ne sont pas lus. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PerksDto(List<PerkStyleDto> styles) {
    }

    /**
     * Arbre de runes.
     *
     * @param description {@code primaryStyle} ou {@code subStyle}
     * @param style       identifiant de l'arbre, ex. 8100 = Domination
     * @param selections  runes choisies dans cet arbre, la keystone en premier pour l'arbre principal
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PerkStyleDto(String description, int style, List<PerkSelectionDto> selections) {

        static final String PRIMARY = "primaryStyle";
        static final String SECONDARY = "subStyle";
    }

    /** Rune choisie. Les valeurs {@code var1…3} (statistiques de la rune) ne sont pas lues. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PerkSelectionDto(int perk) {
    }
}
