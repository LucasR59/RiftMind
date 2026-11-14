package com.riftmind.backend.db;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.riftmind.backend.TestcontainersConfiguration;

/**
 * Remplissage initial de {@code rank_snapshot} par {@code V5__rank_snapshot.sql} (tâche 7.10) : les joueurs déjà
 * connus commencent leur historique avec leur rang actuel.
 *
 * <p>Les migrations sont rejouées dans un schéma à part (jusqu'à V4, un rang, puis V5), supprimé à la fin : le
 * schéma partagé par les autres tests n'est pas touché. Sans transaction de test, pour que Flyway, qui utilise ses
 * propres connexions, voie les lignes insérées.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(TestcontainersConfiguration.class)
class RankSnapshotSeedMigrationTest {

    private static final String SCHEMA = "rank_snapshot_seed_test";

    @Autowired
    private DataSource dataSource;

    private void migrateTo(String version) {
        Flyway.configure().dataSource(dataSource).schemas(SCHEMA).target(version).load().migrate();
    }

    @AfterEach
    void dropSchema() {
        JdbcClient.create(dataSource).sql("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE").update();
    }

    @Test
    void leRangActuelDesJoueursConnusDevientLePremierPoint() {
        JdbcClient jdbc = JdbcClient.create(dataSource);
        migrateTo("4");
        jdbc.sql("""
                INSERT INTO %1$s.player (puuid, game_name, tag_line, platform, summoner_level, profile_icon_id,
                                         last_refreshed_at)
                VALUES ('p1', 'Joueur', 'EUW', 'EUW1', 100, 1, now()),
                       ('p2', 'Non classé', 'EUW', 'EUW1', 30, 1, now())
                """.formatted(SCHEMA)).update();
        jdbc.sql("""
                INSERT INTO %1$s.league_entry (puuid, queue_type, tier, rank, league_points, wins, losses, hot_streak,
                                               updated_at)
                VALUES ('p1', 'RANKED_SOLO_5x5', 'EMERALD', 'III', 37, 40, 35, false, '2026-10-30T12:00:00Z'),
                       ('p1', 'RANKED_FLEX_SR', 'GOLD', 'I', 0, 5, 2, false, '2026-10-29T12:00:00Z')
                """.formatted(SCHEMA)).update();

        migrateTo("5");

        String history = jdbc.sql("""
                SELECT string_agg(puuid || ' ' || queue_type || ' ' || tier || ' ' || rank || ' ' || league_points
                                  || ' ' || wins || '/' || losses || ' '
                                  || to_char(recorded_at AT TIME ZONE 'UTC', 'YYYY-MM-DD'), ' | ' ORDER BY queue_type)
                FROM %s.rank_snapshot
                """.formatted(SCHEMA)).query(String.class).single();
        assertThat(history).isEqualTo("p1 RANKED_FLEX_SR GOLD I 0 5/2 2026-10-29"
                + " | p1 RANKED_SOLO_5x5 EMERALD III 37 40/35 2026-10-30");
    }
}
