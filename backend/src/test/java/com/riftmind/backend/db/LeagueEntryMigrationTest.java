package com.riftmind.backend.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.riftmind.backend.TestcontainersConfiguration;
import com.riftmind.backend.riot.RankedQueue;
import com.riftmind.backend.riot.RiotFixtures;

/**
 * Table {@code league_entry} créée par {@code V3__league_entry.sql} (tâche 3.1), sur un vrai PostgreSQL
 * (Testcontainers). Chaque test est annulé à la fin (transaction de {@code @DataJpaTest}).
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class LeagueEntryMigrationTest {

    private static final String SOLO = RankedQueue.RANKED_SOLO_5x5.name();
    private static final String FLEX = RankedQueue.RANKED_FLEX_SR.name();

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

    private int insertEntry(String puuid, String queueType, int leaguePoints, int wins, int losses) {
        return jdbc.sql("""
                INSERT INTO league_entry (puuid, queue_type, tier, rank, league_points, wins, losses, hot_streak, updated_at)
                VALUES (:puuid, :queueType, 'CHALLENGER', 'I', :lp, :wins, :losses, false, now())
                """)
                .param("puuid", puuid).param("queueType", queueType)
                .param("lp", leaguePoints).param("wins", wins).param("losses", losses)
                .update();
    }

    private int insertEntry(String queueType) {
        return insertEntry(RiotFixtures.PUUID, queueType, 1234, 250, 200);
    }

    private long countEntries(String puuid) {
        return jdbc.sql("SELECT count(*) FROM league_entry WHERE puuid = :puuid")
                .param("puuid", puuid).query(Long.class).single();
    }

    @Test
    void laMigrationV3EstAppliquee() {
        String description = jdbc.sql("SELECT description FROM flyway_schema_history WHERE version = '3' AND success")
                .query(String.class).single();

        assertThat(description).isEqualTo("league entry");
    }

    @Test
    void unJoueurPeutAvoirUnRangParFile() {
        insertEntry(SOLO);
        insertEntry(FLEX);

        assertThat(countEntries(RiotFixtures.PUUID)).isEqualTo(2);
    }

    @Test
    void unJoueurNAQuUnRangParFile() {
        insertEntry(SOLO);

        assertThatThrownBy(() -> insertEntry(SOLO)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void unRangAppartientAUnJoueurConnu() {
        assertThatThrownBy(() -> insertEntry(RiotFixtures.puuid(2), SOLO, 0, 0, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void supprimerUnJoueurSupprimeSesRangs() {
        insertPlayer(RiotFixtures.puuid(2));
        insertEntry(SOLO);
        insertEntry(FLEX);
        insertEntry(RiotFixtures.puuid(2), SOLO, 10, 1, 1);

        jdbc.sql("DELETE FROM player WHERE puuid = :puuid").param("puuid", RiotFixtures.PUUID).update();

        assertThat(countEntries(RiotFixtures.PUUID)).isZero();
        assertThat(countEntries(RiotFixtures.puuid(2))).as("rangs d'un autre joueur").isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(RankedQueue.class)
    void accepteToutesLesFilesDeLEnum(RankedQueue queue) {
        assertThat(insertEntry(queue.name())).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RANKED_FLEX_TT", "RANKED_TFT", "ranked_solo_5x5", ""})
    void refuseUneFileNonAffichee(String queueType) {
        assertThatThrownBy(() -> insertEntry(queueType)).isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Un cas par test : après une erreur, PostgreSQL refuse toute autre requête dans la même transaction. */
    @ParameterizedTest
    @CsvSource({"-1, 0, 0", "0, -1, 0", "0, 0, -1"})
    void refuseDesValeursNegatives(int leaguePoints, int wins, int losses) {
        assertThatThrownBy(() -> insertEntry(RiotFixtures.PUUID, SOLO, leaguePoints, wins, losses))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void lesRangsDUnJoueurSeLisentParLaClePrimaire() {
        insertEntry(SOLO);
        // La table de test est minuscule : sans cela, PostgreSQL préférerait toujours un parcours complet
        jdbc.sql("SET LOCAL enable_seqscan = off").update();

        String plan = String.join("\n", jdbc.sql("EXPLAIN SELECT * FROM league_entry WHERE puuid = :puuid")
                .param("puuid", RiotFixtures.PUUID).query(String.class).list());

        assertThat(plan).contains("league_entry_pkey");
    }
}
