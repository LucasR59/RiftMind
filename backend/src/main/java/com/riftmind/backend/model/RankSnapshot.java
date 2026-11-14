package com.riftmind.backend.model;

import java.time.Instant;
import java.util.Objects;
import java.util.OptionalInt;

import com.riftmind.backend.riot.RankedQueue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Rang d'un joueur dans une file classée à un instant donné (table {@code rank_snapshot},
 * V5__rank_snapshot.sql). Une ligne est ajoutée quand le rang change ; elle n'est jamais modifiée.
 */
@Entity
@Table(name = "rank_snapshot")
public class RankSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String puuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "queue_type", nullable = false, updatable = false)
    private RankedQueue queueType;

    @Column(nullable = false, updatable = false)
    private String tier;

    @Column(nullable = false, updatable = false)
    private String rank;

    @Column(name = "league_points", nullable = false, updatable = false)
    private int leaguePoints;

    @Column(nullable = false, updatable = false)
    private int wins;

    @Column(nullable = false, updatable = false)
    private int losses;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    /** Réservé à JPA. */
    protected RankSnapshot() {
    }

    public RankSnapshot(String puuid, RankedQueue queueType, String tier, String rank, int leaguePoints, int wins,
            int losses, Instant recordedAt) {
        this.puuid = Objects.requireNonNull(puuid, "puuid");
        this.queueType = Objects.requireNonNull(queueType, "queueType");
        this.tier = Objects.requireNonNull(tier, "tier");
        this.rank = Objects.requireNonNull(rank, "rank");
        this.leaguePoints = leaguePoints;
        this.wins = wins;
        this.losses = losses;
        this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
    }

    /** Photographie du rang actuel. */
    public static RankSnapshot of(LeagueEntry entry, Instant recordedAt) {
        return new RankSnapshot(entry.getPuuid(), entry.getQueueType(), entry.getTier(), entry.getRank(),
                entry.getLeaguePoints(), entry.getWins(), entry.getLosses(), recordedAt);
    }

    /** Vrai si le rang (palier, division, LP) ou le bilan (victoires, défaites) diffère de celui de l'entrée. */
    public boolean differsFrom(LeagueEntry entry) {
        return !tier.equals(entry.getTier()) || !rank.equals(entry.getRank())
                || leaguePoints != entry.getLeaguePoints() || wins != entry.getWins() || losses != entry.getLosses();
    }

    /** Score du rang (voir {@link RankScore}), vide pour un palier inconnu. */
    public OptionalInt score() {
        return RankScore.of(tier, rank, leaguePoints);
    }

    public Long getId() {
        return id;
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

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
