package com.riftmind.backend.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import org.springframework.data.domain.Persistable;

import com.riftmind.backend.riot.dto.ParticipantDto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * Statistiques d'un joueur dans une partie (table {@code match_participant}, V4__match.sql). Comme {@link Match},
 * une ligne ne change jamais.
 *
 * <p>Clé : (match, numéro du participant). Le PUUID n'est pas unique dans une partie : les bots partagent le même.
 * Pas de relation JPA vers {@link Match} : les deux sont enregistrés ensemble par l'ingestion, puis lus par des
 * requêtes dédiées. {@code gameStart} et {@code queueId} sont recopiés depuis le match par {@link #of}.
 */
@Entity
@Table(name = "match_participant")
@IdClass(MatchParticipant.Key.class)
public class MatchParticipant implements Persistable<MatchParticipant.Key> {

    /** Clé primaire composée. Les noms des champs sont ceux des attributs {@code @Id} de l'entité. */
    public record Key(String matchId, int participantId) implements Serializable {
    }

    @Id
    @Column(name = "match_id")
    private String matchId;

    @Id
    @Column(name = "participant_id")
    private int participantId;

    @Column(nullable = false)
    private String puuid;

    @Column(name = "game_name")
    private String gameName;

    @Column(name = "tag_line")
    private String tagLine;

    @Column(name = "team_id", nullable = false)
    private int teamId;

    @Column(name = "champion_id", nullable = false)
    private int championId;

    @Column(name = "champion_name", nullable = false)
    private String championName;

    @Column(name = "champ_level", nullable = false)
    private int champLevel;

    @Column(nullable = false)
    private String position;

    @Column(nullable = false)
    private boolean win;

    @Column(nullable = false)
    private int kills;

    @Column(nullable = false)
    private int deaths;

    @Column(nullable = false)
    private int assists;

    @Column(nullable = false)
    private int cs;

    @Column(nullable = false)
    private int gold;

    @Column(name = "damage_champions", nullable = false)
    private int damageChampions;

    @Column(name = "vision_score", nullable = false)
    private int visionScore;

    /** {@code integer[]} en base : 6 cases d'inventaire puis la balise. */
    @Column(nullable = false)
    private int[] items;

    @Column(name = "summoner_spells", nullable = false)
    private int[] summonerSpells;

    @Column(nullable = false)
    private int keystone;

    @Column(name = "secondary_style", nullable = false)
    private int secondaryStyle;

    @Column(name = "game_start", nullable = false)
    private Instant gameStart;

    @Column(name = "queue_id", nullable = false)
    private int queueId;

    @Transient
    private boolean isNew = true;

    /** Réservé à JPA. */
    protected MatchParticipant() {
    }

    /**
     * Participant tel que Riot l'a renvoyé dans ce match. Les champs absents chez Riot (position hors Faille, Riot ID
     * des bots) deviennent une chaîne vide ou {@code null}.
     */
    public static MatchParticipant of(Match match, ParticipantDto dto) {
        MatchParticipant p = new MatchParticipant();
        p.matchId = match.getMatchId();
        p.participantId = dto.participantId();
        p.puuid = Objects.requireNonNull(dto.puuid(), "puuid");
        p.gameName = blankToNull(dto.riotIdGameName());
        p.tagLine = blankToNull(dto.riotIdTagline());
        p.teamId = dto.teamId();
        p.championId = dto.championId();
        p.championName = Objects.requireNonNullElse(dto.championName(), "");
        p.champLevel = dto.champLevel();
        p.position = Objects.requireNonNullElse(dto.teamPosition(), "");
        p.win = dto.win();
        p.kills = dto.kills();
        p.deaths = dto.deaths();
        p.assists = dto.assists();
        p.cs = dto.cs();
        p.gold = dto.goldEarned();
        p.damageChampions = dto.totalDamageDealtToChampions();
        p.visionScore = dto.visionScore();
        p.items = dto.items();
        p.summonerSpells = dto.summonerSpells();
        p.keystone = dto.keystone();
        p.secondaryStyle = dto.secondaryStyle();
        p.gameStart = match.getGameStart();
        p.queueId = match.getQueueId();
        return p;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public Key getId() {
        return new Key(matchId, participantId);
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public String getMatchId() {
        return matchId;
    }

    public int getParticipantId() {
        return participantId;
    }

    public String getPuuid() {
        return puuid;
    }

    /** Nom du Riot ID au moment de la partie, {@code null} s'il est inconnu (bots, anciennes parties). */
    public String getGameName() {
        return gameName;
    }

    public String getTagLine() {
        return tagLine;
    }

    public int getTeamId() {
        return teamId;
    }

    public int getChampionId() {
        return championId;
    }

    public String getChampionName() {
        return championName;
    }

    public int getChampLevel() {
        return champLevel;
    }

    /** {@code TOP}, {@code JUNGLE}, {@code MIDDLE}, {@code BOTTOM}, {@code UTILITY}, ou vide hors Faille. */
    public String getPosition() {
        return position;
    }

    public boolean isWin() {
        return win;
    }

    public int getKills() {
        return kills;
    }

    public int getDeaths() {
        return deaths;
    }

    public int getAssists() {
        return assists;
    }

    public int getCs() {
        return cs;
    }

    public int getGold() {
        return gold;
    }

    public int getDamageChampions() {
        return damageChampions;
    }

    public int getVisionScore() {
        return visionScore;
    }

    /** Copie des 7 cases d'objets (6 d'inventaire puis la balise), 0 = case vide. */
    public int[] getItems() {
        return items.clone();
    }

    /** Copie des 2 sorts d'invocateur. */
    public int[] getSummonerSpells() {
        return summonerSpells.clone();
    }

    public int getKeystone() {
        return keystone;
    }

    public int getSecondaryStyle() {
        return secondaryStyle;
    }

    public Instant getGameStart() {
        return gameStart;
    }

    public int getQueueId() {
        return queueId;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof MatchParticipant that && matchId != null
                && matchId.equals(that.getMatchId()) && participantId == that.getParticipantId();
    }

    @Override
    public int hashCode() {
        return Objects.hash(matchId, participantId);
    }

    @Override
    public String toString() {
        return "MatchParticipant[" + matchId + " #" + participantId + ", " + championName + " " + kills + "/"
                + deaths + "/" + assists + ", puuid=" + puuid + "]";
    }
}
