package com.riftmind.backend.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.riftmind.backend.TestcontainersConfiguration;
import com.riftmind.backend.riot.RiotFixtures;

/**
 * Table {@code rank_snapshot} créée par {@code V5__rank_snapshot.sql} (tâche 7.10), sur un vrai PostgreSQL
 * (Testcontainers). Chaque test est annulé à la fin (transaction de {@code @DataJpaTest}). Le remplissage initial
 * est testé à part : {@link RankSnapshotSeedMigrationTest}.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class RankSnapshotMigrationTest {

    @Autowired
    private DataSource dataSource;

    private JdbcClient jdbc;

    @BeforeEach
    void setUp() {
        jdbc = JdbcClient.create(dataSource);
        insertPlayer(RiotFixtures.PUUID);
    }

    private void insertPlayer(String puuid) {
        jdbc.sql("""
                INSERT INTO player (puuid, game_name, tag_line, platform, summoner_level, profile_icon_id, last_refreshed_at)
                VALUES (:puuid, 'Joueur Test', 'EUW', 'EUW1', 512, 7, now())
                """).param("puuid", puuid).update();
    }

    private int insertSnapshot(String puuid, String queueType, int leaguePoints, int wins, int losses) {
        return jdbc.sql("""
                INSERT INTO rank_snapshot (puuid, queue_type, tier, rank, league_points, wins, losses, recorded_at)
                VALUES (:puuid, :queueType, 'GOLD', 'II', :lp, :wins, :losses, now())
                """)
                .param("puuid", puuid).param("queueType", queueType)
                .param("lp", leaguePoints).param("wins", wins).param("losses", losses)
                .update();
    }

    private long countSnapshots(String puuid) {
        return jdbc.sql("SELECT count(*) FROM rank_snapshot WHERE puuid = :puuid")
                .param("puuid", puuid).query(Long.class).single();
    }

    @Test
    void laMigrationV5EstAppliquee() {
        String description = jdbc.sql("SELECT description FROM flyway_schema_history WHERE version = '5' AND success")
                .query(String.class).single();

        assertThat(description).isEqualTo("rank snapshot");
    }

    @Test
    void unJoueurPeutAvoirPlusieursRangsSuccessifsDansUneFile() {
        insertSnapshot(RiotFixtures.PUUID, "RANKED_SOLO_5x5", 45, 10, 8);
        insertSnapshot(RiotFixtures.PUUID, "RANKED_SOLO_5x5", 65, 11, 8);
        insertSnapshot(RiotFixtures.PUUID, "RANKED_FLEX_SR", 0, 1, 0);

        assertThat(countSnapshots(RiotFixtures.PUUID)).isEqualTo(3);
    }

    @Test
    void supprimerUnJoueurSupprimeSonHistorique() {
        insertPlayer(RiotFixtures.puuid(2));
        insertSnapshot(RiotFixtures.PUUID, "RANKED_SOLO_5x5", 45, 10, 8);
        insertSnapshot(RiotFixtures.puuid(2), "RANKED_SOLO_5x5", 10, 1, 1);

        jdbc.sql("DELETE FROM player WHERE puuid = :puuid").param("puuid", RiotFixtures.PUUID).update();

        assertThat(countSnapshots(RiotFixtures.PUUID)).isZero();
        assertThat(countSnapshots(RiotFixtures.puuid(2))).as("historique d'un autre joueur").isEqualTo(1);
    }

    @Test
    void unRangAppartientAUnJoueurConnu() {
        assertThatThrownBy(() -> insertSnapshot(RiotFixtures.puuid(2), "RANKED_SOLO_5x5", 0, 0, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RANKED_FLEX_TT", "RANKED_TFT", "ranked_solo_5x5", ""})
    void refuseUneFileNonAffichee(String queueType) {
        assertThatThrownBy(() -> insertSnapshot(RiotFixtures.PUUID, queueType, 0, 0, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Un cas par test : après une erreur, PostgreSQL refuse toute autre requête dans la même transaction. */
    @ParameterizedTest
    @CsvSource({"-1, 0, 0", "0, -1, 0", "0, 0, -1"})
    void refuseDesValeursNegatives(int leaguePoints, int wins, int losses) {
        assertThatThrownBy(() -> insertSnapshot(RiotFixtures.PUUID, "RANKED_SOLO_5x5", leaguePoints, wins, losses))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
