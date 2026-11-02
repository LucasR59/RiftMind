package com.riftmind.backend.model;

import java.time.Instant;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * Partie League of Legends (table {@code match}, V4__match.sql). Une partie terminée ne change jamais : l'entité n'a
 * pas de méthode de modification. Les statistiques des joueurs sont dans {@link MatchParticipant}.
 *
 * <p>La réponse brute de Riot ({@link #getRaw()}, ~120 Ko) est lue avec l'entité : pour lister des parties, passer par
 * {@link MatchParticipant} plutôt que de charger des {@code Match}.
 */
@Entity
@Table(name = "match")
public class Match implements Persistable<String> {

    @Id
    @Column(name = "match_id")
    private String matchId;

    @Column(nullable = false)
    private String platform;

    @Column(name = "queue_id", nullable = false)
    private int queueId;

    @Column(name = "game_version", nullable = false)
    private String gameVersion;

    @Column(name = "game_start", nullable = false)
    private Instant gameStart;

    @Column(name = "duration_s", nullable = false)
    private int durationSeconds;

    @Column(nullable = false)
    private boolean remake;

    /** JSON stocké en {@code jsonb} : PostgreSQL le valide, et il peut être interrogé en SQL ({@code raw -> 'info'}). */
    @JdbcTypeCode(SqlTypes.JSON)
    private String raw;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    /** Réservé à JPA. */
    protected Match() {
    }

    /**
     * @param platform        {@code info.platformId} de Riot, ex. {@code EUW1}
     * @param durationSeconds durée en secondes
     * @param raw             réponse brute de Riot (JSON), ou {@code null} si elle ne doit pas être gardée
     * @param createdAt       date d'enregistrement chez RiftMind
     */
    public Match(String matchId, String platform, int queueId, String gameVersion, Instant gameStart,
            int durationSeconds, boolean remake, String raw, Instant createdAt) {
        this.matchId = Objects.requireNonNull(matchId, "matchId");
        this.platform = Objects.requireNonNull(platform, "platform");
        this.queueId = queueId;
        this.gameVersion = Objects.requireNonNull(gameVersion, "gameVersion");
        this.gameStart = Objects.requireNonNull(gameStart, "gameStart");
        this.durationSeconds = durationSeconds;
        this.remake = remake;
        this.raw = raw;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public String getId() {
        return matchId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public String getMatchId() {
        return matchId;
    }

    public String getPlatform() {
        return platform;
    }

    public int getQueueId() {
        return queueId;
    }

    public String getGameVersion() {
        return gameVersion;
    }

    public Instant getGameStart() {
        return gameStart;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    /** Partie terminée par un remake : à exclure des statistiques. */
    public boolean isRemake() {
        return remake;
    }

    /** Réponse brute de Riot (JSON), ou {@code null} si elle n'a pas été gardée. */
    public String getRaw() {
        return raw;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Match that && matchId != null && matchId.equals(that.getMatchId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(matchId);
    }

    @Override
    public String toString() {
        return "Match[" + matchId + ", file " + queueId + ", " + gameStart + "]";
    }
}
