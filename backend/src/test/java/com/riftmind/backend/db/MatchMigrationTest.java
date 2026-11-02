package com.riftmind.backend.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.riftmind.backend.TestcontainersConfiguration;
import com.riftmind.backend.riot.RiotFixtures;

/**
 * Tables {@code match} et {@code match_participant} créées par {@code V4__match.sql} (tâche 4.2), sur un vrai
 * PostgreSQL (Testcontainers). Chaque test est annulé à la fin (transaction de {@code @DataJpaTest}).
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class MatchMigrationTest {

    private static final String MATCH_ID = RiotFixtures.MATCH_ID;

    @Autowired
    private DataSource dataSource;

    private JdbcClient jdbc;

    @BeforeEach
    void setUp() {
        jdbc = JdbcClient.create(dataSource);
    }

    private int insertMatch(String matchId, String raw) {
        return jdbc.sql("""
                INSERT INTO match (match_id, platform, queue_id, game_version, game_start, duration_s, raw)
                VALUES (:matchId, 'EUW1', 420, '16.21.834.2754', now(), 1463, CAST(:raw AS jsonb))
                """).param("matchId", matchId).param("raw", raw).update();
    }

    private int insertParticipant(String matchId, int participantId, String puuid, String items, String spells) {
        return jdbc.sql("""
                INSERT INTO match_participant (match_id, participant_id, puuid, game_name, tag_line, team_id,
                    champion_id, champion_name, champ_level, position, win, kills, deaths, assists, cs, gold,
                    damage_champions, vision_score, items, summoner_spells, keystone, secondary_style,
                    game_start, queue_id)
                VALUES (:matchId, :participantId, :puuid, 'Joueur Test', 'EUW', 100, 429, 'Kalista', 15, 'BOTTOM',
                    true, 6, 4, 3, 250, 13852, 19708, 19, CAST(:items AS integer[]), CAST(:spells AS integer[]),
                    8008, 8400, now(), 420)
                """)
                .param("matchId", matchId).param("participantId", participantId).param("puuid", puuid)
                .param("items", items).param("spells", spells)
                .update();
    }

    private int insertParticipant(int participantId, String puuid) {
        return insertParticipant(MATCH_ID, participantId, puuid, "{1086,3087,3124,1082,3302,1011,3363}", "{4,21}");
    }

    private long countParticipants(String matchId) {
        return jdbc.sql("SELECT count(*) FROM match_participant WHERE match_id = :matchId")
                .param("matchId", matchId).query(Long.class).single();
    }

    private String explain(String sql) {
        // Les tables de test sont minuscules : sans cela, PostgreSQL préférerait toujours un parcours complet
        jdbc.sql("SET LOCAL enable_seqscan = off").update();
        return String.join("\n", jdbc.sql("EXPLAIN " + sql).param("puuid", RiotFixtures.PUUID)
                .query(String.class).list());
    }

    @Test
    void laMigrationV4EstAppliquee() {
        String description = jdbc.sql("SELECT description FROM flyway_schema_history WHERE version = '4' AND success")
                .query(String.class).single();

        assertThat(description).isEqualTo("match");
    }

    @Test
    void enregistreUnMatchAvecSaReponseBrute() {
        insertMatch(MATCH_ID, RiotFixtures.read(RiotFixtures.MATCH));

        var row = jdbc.sql("""
                SELECT remake, created_at, raw -> 'info' ->> 'queueId' AS queue FROM match WHERE match_id = :id
                """).param("id", MATCH_ID).query().singleRow();

        assertThat(row).containsEntry("remake", false).containsEntry("queue", "420");
        assertThat(row.get("created_at")).isNotNull();
    }

    @Test
    void laReponseBruteEstFacultative() {
        assertThat(insertMatch(MATCH_ID, null)).isEqualTo(1);
    }

    @Test
    void unMatchNEstEnregistreQuUneFois() {
        insertMatch(MATCH_ID, null);

        assertThatThrownBy(() -> insertMatch(MATCH_ID, null)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void lesObjetsEtLesSortsSontRelusDansLOrdre() {
        insertMatch(MATCH_ID, null);
        insertParticipant(4, RiotFixtures.PUUID);

        var row = jdbc.sql("SELECT items, summoner_spells FROM match_participant WHERE match_id = :id")
                .param("id", MATCH_ID)
                .query((rs, n) -> List.of(rs.getArray("items").getArray(), rs.getArray("summoner_spells").getArray()))
                .single();

        assertThat((Integer[]) row.get(0)).containsExactly(1086, 3087, 3124, 1082, 3302, 1011, 3363);
        assertThat((Integer[]) row.get(1)).containsExactly(4, 21);
    }

    @Test
    void deuxBotsPeuventAvoirLeMemePuuidDansUnePartie() {
        insertMatch(MATCH_ID, null);
        insertParticipant(6, "BOT");
        insertParticipant(7, "BOT");

        assertThat(countParticipants(MATCH_ID)).isEqualTo(2);
    }

    @Test
    void unParticipantNApparaitQuUneFoisParPartie() {
        insertMatch(MATCH_ID, null);
        insertParticipant(4, RiotFixtures.PUUID);

        assertThatThrownBy(() -> insertParticipant(4, RiotFixtures.puuid(2)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void unParticipantAppartientAUnMatchConnu() {
        assertThatThrownBy(() -> insertParticipant(4, RiotFixtures.PUUID))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void supprimerUnMatchSupprimeSesParticipants() {
        insertMatch(MATCH_ID, null);
        insertMatch("EUW1_7000000002", null);
        insertParticipant(1, RiotFixtures.puuid(2));
        insertParticipant(4, RiotFixtures.PUUID);
        insertParticipant("EUW1_7000000002", 4, RiotFixtures.PUUID, "{0,0,0,0,0,0,0}", "{4,7}");

        jdbc.sql("DELETE FROM match WHERE match_id = :id").param("id", MATCH_ID).update();

        assertThat(countParticipants(MATCH_ID)).isZero();
        assertThat(countParticipants("EUW1_7000000002")).as("participants d'un autre match").isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{1,2,3,4,5,6}", "{1,2,3,4,5,6,7,8}", "{}"})
    void refuseUnNombreDObjetsDifferentDe7(String items) {
        insertMatch(MATCH_ID, null);

        assertThatThrownBy(() -> insertParticipant(MATCH_ID, 4, RiotFixtures.PUUID, items, "{4,21}"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refuseUnNombreDeSortsDifferentDe2() {
        insertMatch(MATCH_ID, null);

        assertThatThrownBy(() -> insertParticipant(MATCH_ID, 4, RiotFixtures.PUUID,
                "{0,0,0,0,0,0,0}", "{4}"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void lHistoriqueDUnJoueurUtiliseLIndexParDate() {
        String plan = explain("""
                SELECT match_id FROM match_participant WHERE puuid = :puuid ORDER BY game_start DESC LIMIT 20
                """);

        assertThat(plan).contains("match_participant_puuid_game_start_idx");
    }

    @Test
    void lesStatsParChampionUtilisentLIndexParChampion() {
        String plan = explain("""
                SELECT champion_id, count(*) FROM match_participant WHERE puuid = :puuid AND champion_id = 429
                GROUP BY champion_id
                """);

        assertThat(plan).contains("match_participant_puuid_champion_idx");
    }
}
