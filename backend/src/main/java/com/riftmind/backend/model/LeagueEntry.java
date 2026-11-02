package com.riftmind.backend.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import org.springframework.data.domain.Persistable;

import com.riftmind.backend.riot.RankedQueue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * Rang d'un joueur dans une file classée (table {@code league_entry}, V3__league_entry.sql). Identifié par le couple
 * (PUUID, file) : un joueur a au plus une ligne par file, et aucune s'il n'est pas classé.
 *
 * <p>Comme pour {@link Player}, la clé est fournie et non générée : {@link Persistable} évite un {@code SELECT}
 * avant chaque {@code INSERT}.
 */
@Entity
@Table(name = "league_entry")
@IdClass(LeagueEntry.Key.class)
public class LeagueEntry implements Persistable<LeagueEntry.Key> {

    /** Clé primaire composée. Les noms des champs sont ceux des attributs {@code @Id} de l'entité. */
    public record Key(String puuid, RankedQueue queueType) implements Serializable {
    }

    @Id
    private String puuid;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "queue_type")
    private RankedQueue queueType;

    @Column(nullable = false)
    private String tier;

    @Column(nullable = false)
    private String rank;

    @Column(name = "league_points", nullable = false)
    private int leaguePoints;

    @Column(nullable = false)
    private int wins;

    @Column(nullable = false)
    private int losses;

    @Column(name = "hot_streak", nullable = false)
    private boolean hotStreak;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Vrai tant que l'entité n'a été ni enregistrée ni lue en base. */
    @Transient
    private boolean isNew = true;

    /** Réservé à JPA. */
    protected LeagueEntry() {
    }

    /** Nouveau rang, tel que Riot vient de le renvoyer. */
    public LeagueEntry(String puuid, RankedQueue queueType, String tier, String rank, int leaguePoints, int wins,
            int losses, boolean hotStreak, Instant updatedAt) {
        this.puuid = Objects.requireNonNull(puuid, "puuid");
        this.queueType = Objects.requireNonNull(queueType, "queueType");
        apply(tier, rank, leaguePoints, wins, losses, hotStreak, updatedAt);
    }

    /** Remplace le rang par les dernières données de Riot. */
    public void update(String tier, String rank, int leaguePoints, int wins, int losses, boolean hotStreak,
            Instant updatedAt) {
        apply(tier, rank, leaguePoints, wins, losses, hotStreak, updatedAt);
    }

    private void apply(String tier, String rank, int leaguePoints, int wins, int losses, boolean hotStreak,
            Instant updatedAt) {
        this.tier = Objects.requireNonNull(tier, "tier");
        this.rank = Objects.requireNonNull(rank, "rank");
        this.leaguePoints = leaguePoints;
        this.wins = wins;
        this.losses = losses;
        this.hotStreak = hotStreak;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public Key getId() {
        return new Key(puuid, queueType);
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public String getPuuid() {
        return puuid;
    }

    public RankedQueue getQueueType() {
        return queueType;
    }

    public String getTier() {
        return tier;
    }

    public String getRank() {
        return rank;
    }

    public int getLeaguePoints() {
        return leaguePoints;
    }

    public int getWins() {
        return wins;
    }

    public int getLosses() {
        return losses;
    }

    public boolean isHotStreak() {
        return hotStreak;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Deux rangs sont égaux s'ils concernent le même joueur et la même file. */
    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof LeagueEntry that && puuid != null
                && puuid.equals(that.getPuuid()) && queueType == that.getQueueType();
    }

    @Override
    public int hashCode() {
        return Objects.hash(puuid, queueType);
    }

    @Override
    public String toString() {
        return "LeagueEntry[" + queueType + " " + tier + " " + rank + " " + leaguePoints + " LP, puuid=" + puuid
                + "]";
    }
}
