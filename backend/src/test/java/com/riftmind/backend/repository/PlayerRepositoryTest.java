package com.riftmind.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;

import com.riftmind.backend.TestcontainersConfiguration;
import com.riftmind.backend.model.Player;
import com.riftmind.backend.riot.Platform;
import com.riftmind.backend.riot.RiotFixtures;

/**
 * Entité {@link Player} et {@link PlayerRepository} sur un vrai PostgreSQL (tâche 2.3).
 * Hibernate démarre en {@code ddl-auto=validate} : si l'entité ne correspond pas à la table, le test échoue.
 */
@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.show-sql=true"})
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class PlayerRepositoryTest {

    /** Précision de PostgreSQL : la microseconde. */
    private static final Instant FETCHED_AT = Instant.parse("2026-11-01T20:00:00.123456Z");

    @Autowired
    private PlayerRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        statistics = entityManager.getEntityManager().getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
    }

    private static Player player(String puuid, String gameName, String tagLine, Instant fetchedAt) {
        return new Player(puuid, gameName, tagLine, Platform.EUW1, 512, 7, fetchedAt);
    }

    private static Player mainPlayer() {
        return player(RiotFixtures.PUUID, RiotFixtures.GAME_NAME, RiotFixtures.TAG_LINE, FETCHED_AT);
    }

    /** Enregistre en base puis vide le cache JPA : la lecture suivante vient vraiment de PostgreSQL. */
    private void persistAndClear(Player... players) {
        for (Player p : players) {
            repository.save(p);
        }
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void enregistreEtRelitUnJoueur() {
        persistAndClear(mainPlayer());

        Player found = repository.findById(RiotFixtures.PUUID).orElseThrow();

        assertThat(found.getGameName()).isEqualTo("Joueur Test");
        assertThat(found.getTagLine()).isEqualTo("EUW");
        assertThat(found.getRiotId()).isEqualTo("Joueur Test#EUW");
        assertThat(found.getPlatform()).isEqualTo(Platform.EUW1);
        assertThat(found.getSummonerLevel()).isEqualTo(512);
        assertThat(found.getProfileIconId()).isEqualTo(7);
        assertThat(found.getLastRefreshedAt()).isEqualTo(FETCHED_AT);
        assertThat(found.getCreatedAt()).isEqualTo(FETCHED_AT);
        assertThat(found.isNew()).isFalse();
    }

    @Test
    void laPlateformeEstStockeeAvecLeNomDeLEnum() {
        persistAndClear(mainPlayer());

        Object stored = entityManager.getEntityManager()
                .createNativeQuery("SELECT platform FROM player WHERE puuid = ?1")
                .setParameter(1, RiotFixtures.PUUID).getSingleResult();

        assertThat(stored).isEqualTo("EUW1");
    }

    @Test
    void unNouveauJoueurEstInsereSansSelectPrealable() {
        Player player = mainPlayer();
        assertThat(player.isNew()).isTrue();
        statistics.clear();

        repository.save(player);
        entityManager.flush();

        // Un seul ordre SQL, l'INSERT. Sans Persistable, Spring Data ferait d'abord un SELECT (PUUID non généré).
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(statistics.getEntityInsertCount()).isEqualTo(1);
        assertThat(player.isNew()).isFalse();
    }

    @Test
    void unRafraichissementMetAJourLeJoueurSansToucherALaDateDeCreation() {
        persistAndClear(mainPlayer());
        Instant later = FETCHED_AT.plusSeconds(3600);

        Player player = repository.findById(RiotFixtures.PUUID).orElseThrow();
        player.refresh("Nouveau Nom", "NEW", Platform.EUN1, 513, 42, later);
        persistAndClear(player);

        Player reloaded = repository.findById(RiotFixtures.PUUID).orElseThrow();
        assertThat(reloaded.getRiotId()).isEqualTo("Nouveau Nom#NEW");
        assertThat(reloaded.getPlatform()).isEqualTo(Platform.EUN1);
        assertThat(reloaded.getSummonerLevel()).isEqualTo(513);
        assertThat(reloaded.getProfileIconId()).isEqualTo(42);
        assertThat(reloaded.getLastRefreshedAt()).isEqualTo(later);
        assertThat(reloaded.getCreatedAt()).isEqualTo(FETCHED_AT);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void chercheUnRiotIdSansTenirCompteDeLaCasseNiDesAccents() {
        persistAndClear(mainPlayer(), player(RiotFixtures.puuid(2), "Été Ünïcödé", "Fr1", FETCHED_AT));

        assertThat(repository.findByRiotId(Platform.EUW1, "JOUEUR TEST", "euw"))
                .map(Player::getPuuid).contains(RiotFixtures.PUUID);
        assertThat(repository.findByRiotId(Platform.EUW1, "éTÉ üNÏCÖDÉ", "FR1"))
                .map(Player::getPuuid).contains(RiotFixtures.puuid(2));
        // Même Riot ID, autre plateforme : introuvable
        assertThat(repository.findByRiotId(Platform.NA1, RiotFixtures.GAME_NAME, RiotFixtures.TAG_LINE)).isEmpty();
        assertThat(repository.findByRiotId(Platform.EUW1, "Inconnu", "EUW")).isEmpty();
    }

    @Test
    void quandDeuxJoueursPortentLeMemeRiotIdLePlusRecentGagne() {
        // L'ancien propriétaire du nom (puuid 2) n'a pas été rafraîchi depuis un an
        Player former = player(RiotFixtures.puuid(2), RiotFixtures.GAME_NAME, RiotFixtures.TAG_LINE,
                FETCHED_AT.minusSeconds(365L * 24 * 3600));
        persistAndClear(former, mainPlayer());

        assertThat(repository.findAllByRiotId(Platform.EUW1, RiotFixtures.GAME_NAME, RiotFixtures.TAG_LINE))
                .extracting(Player::getPuuid).containsExactly(RiotFixtures.PUUID, RiotFixtures.puuid(2));
        assertThat(repository.findByRiotId(Platform.EUW1, RiotFixtures.GAME_NAME, RiotFixtures.TAG_LINE))
                .map(Player::getPuuid).contains(RiotFixtures.PUUID);
    }

    @Test
    void laRequeteGenereeAppliqueLowerSurLesColonnesCommeLIndex(CapturedOutput output) {
        repository.findByRiotId(Platform.EUW1, "x", "y");

        // Même expression que l'index player_riot_id_idx : lower(game_name), lower(tag_line)
        assertThat(output.getOut())
                .containsPattern("lower\\(\\w+\\.game_name\\)=lower\\(\\?\\)")
                .containsPattern("lower\\(\\w+\\.tag_line\\)=lower\\(\\?\\)");
    }

    @Test
    void deuxInstancesAvecLeMemePuuidSontEgales() {
        Player a = mainPlayer();
        Player b = player(RiotFixtures.PUUID, "Autre", "TAG", FETCHED_AT);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(player(RiotFixtures.puuid(2), "Joueur Test", "EUW", FETCHED_AT));
    }
}
