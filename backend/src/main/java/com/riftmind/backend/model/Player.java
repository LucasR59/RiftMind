package com.riftmind.backend.model;

import java.time.Instant;
import java.util.Objects;

import org.springframework.data.domain.Persistable;

import com.riftmind.backend.riot.Platform;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * Joueur recherché sur RiftMind : un compte Riot, identifié par son PUUID (table {@code player}, V2__player.sql).
 *
 * <p>Le Riot ID, la plateforme, le niveau et l'icône peuvent changer. Ils sont remplacés à chaque rafraîchissement
 * par {@link #refresh}. Seuls le PUUID et la date de création ne bougent jamais.
 *
 * <p>Le PUUID est fourni par Riot, et non généré par la base. {@link Persistable} indique donc à Spring Data si
 * l'entité est nouvelle. Sans cela, {@code save()} ferait un {@code SELECT} avant chaque {@code INSERT}.
 */
@Entity
@Table(name = "player")
public class Player implements Persistable<String> {

    @Id
    private String puuid;

    @Column(name = "game_name", nullable = false)
    private String gameName;

    @Column(name = "tag_line", nullable = false)
    private String tagLine;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;

    @Column(name = "summoner_level", nullable = false)
    private int summonerLevel;

    @Column(name = "profile_icon_id", nullable = false)
    private int profileIconId;

    @Column(name = "last_refreshed_at", nullable = false)
    private Instant lastRefreshedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Vrai tant que l'entité n'a été ni enregistrée ni lue en base. */
    @Transient
    private boolean isNew = true;

    /** Réservé à JPA. */
    protected Player() {
    }

    /**
     * Nouveau joueur, tel que Riot vient de le renvoyer.
     *
     * @param fetchedAt instant de l'appel à Riot : devient la date de création et du dernier rafraîchissement
     */
    public Player(String puuid, String gameName, String tagLine, Platform platform, int summonerLevel,
            int profileIconId, Instant fetchedAt) {
        this.puuid = Objects.requireNonNull(puuid, "puuid");
        this.createdAt = Objects.requireNonNull(fetchedAt, "fetchedAt");
        apply(gameName, tagLine, platform, summonerLevel, profileIconId, fetchedAt);
    }

    /** Met à jour le joueur avec les dernières données de Riot. */
    public void refresh(String gameName, String tagLine, Platform platform, int summonerLevel,
            int profileIconId, Instant refreshedAt) {
        apply(gameName, tagLine, platform, summonerLevel, profileIconId, refreshedAt);
    }

    /** Commun au constructeur et à {@link #refresh} : une méthode privée, non surchargeable par un proxy Hibernate. */
    private void apply(String gameName, String tagLine, Platform platform, int summonerLevel,
            int profileIconId, Instant refreshedAt) {
        this.gameName = Objects.requireNonNull(gameName, "gameName");
        this.tagLine = Objects.requireNonNull(tagLine, "tagLine");
        this.platform = Objects.requireNonNull(platform, "platform");
        this.summonerLevel = summonerLevel;
        this.profileIconId = profileIconId;
        this.lastRefreshedAt = Objects.requireNonNull(refreshedAt, "refreshedAt");
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public String getId() {
        return puuid;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public String getPuuid() {
        return puuid;
    }

    public String getGameName() {
        return gameName;
    }

    public String getTagLine() {
        return tagLine;
    }

    /** Riot ID affiché, ex. {@code Joueur Test#EUW}. */
    public String getRiotId() {
        return gameName + "#" + tagLine;
    }

    public Platform getPlatform() {
        return platform;
    }

    public int getSummonerLevel() {
        return summonerLevel;
    }

    public int getProfileIconId() {
        return profileIconId;
    }

    public Instant getLastRefreshedAt() {
        return lastRefreshedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Deux joueurs sont égaux s'ils ont le même PUUID. {@code getPuuid()} fonctionne aussi sur un proxy Hibernate. */
    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Player that && puuid != null && puuid.equals(that.getPuuid());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(puuid);
    }

    @Override
    public String toString() {
        return "Player[" + getRiotId() + " sur " + platform + ", puuid=" + puuid + "]";
    }
}
