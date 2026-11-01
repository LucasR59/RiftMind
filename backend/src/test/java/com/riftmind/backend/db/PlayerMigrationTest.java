package com.riftmind.backend.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.riftmind.backend.TestcontainersConfiguration;
import com.riftmind.backend.riot.Platform;
import com.riftmind.backend.riot.RiotFixtures;

/**
 * Table {@code player} créée par {@code V2__player.sql} (tâche 2.2), sur un vrai PostgreSQL (Testcontainers).
 * Chaque test est annulé à la fin (transaction de {@code @DataJpaTest}).
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class PlayerMigrationTest {

    private static final String INSERT = """
            INSERT INTO player (puuid, game_name, tag_line, platform, summoner_level, profile_icon_id, last_refreshed_at)
            VALUES (:puuid, :gameName, :tagLine, :platform, :level, :icon, now())
            """;

    /** Requête de recherche par Riot ID, sous la forme qu'utilise l'index (voir le commentaire de la migration). */
    private static final String FIND_BY_RIOT_ID = """
            SELECT puuid FROM player
            WHERE platform = :platform AND lower(game_name) = lower(:gameName) AND lower(tag_line) = lower(:tagLine)
            """;

    @Autowired
    private DataSource dataSource;

    private JdbcClient jdbc;

    @BeforeEach
    void setUp() {
        jdbc = JdbcClient.create(dataSource);
    }

    private int insert(String puuid, String gameName, String tagLine, String platform, int level) {
        return jdbc.sql(INSERT)
                .param("puuid", puuid).param("gameName", gameName).param("tagLine", tagLine)
                .param("platform", platform).param("level", level).param("icon", 7)
                .update();
    }

    private void insertMainPlayer() {
        insert(RiotFixtures.PUUID, RiotFixtures.GAME_NAME, RiotFixtures.TAG_LINE, Platform.EUW1.name(), 512);
    }

    private List<String> findByRiotId(String platform, String gameName, String tagLine) {
        return jdbc.sql(FIND_BY_RIOT_ID)
                .param("platform", platform).param("gameName", gameName).param("tagLine", tagLine)
                .query(String.class).list();
    }

    @Test
    void laMigrationV2EstAppliquee() {
        String description = jdbc.sql("SELECT description FROM flyway_schema_history WHERE version = '2' AND success")
                .query(String.class).single();

        assertThat(description).isEqualTo("player");
    }

    @Test
    void enregistreUnJoueurAvecUneDateDeCreationParDefaut() {
        insertMainPlayer();

        var row = jdbc.sql("SELECT game_name, tag_line, summoner_level, created_at FROM player WHERE puuid = :puuid")
                .param("puuid", RiotFixtures.PUUID).query().singleRow();

        assertThat(row).containsEntry("game_name", "Joueur Test").containsEntry("tag_line", "EUW")
                .containsEntry("summoner_level", 512);
        assertThat(row.get("created_at")).isNotNull();
    }

    @Test
    void leRiotIdSeChercheSansTenirCompteDeLaCasseNiDesAccents() {
        insertMainPlayer();
        insert(RiotFixtures.puuid(2), "Été Ünïcödé", "Fr1", "EUW1", 30);

        assertThat(findByRiotId("EUW1", "JOUEUR TEST", "euw")).containsExactly(RiotFixtures.PUUID);
        assertThat(findByRiotId("EUW1", "éTÉ üNÏCÖDÉ", "FR1")).containsExactly(RiotFixtures.puuid(2));
        // Même Riot ID sur une autre plateforme : pas le même joueur
        assertThat(findByRiotId("NA1", "Joueur Test", "EUW")).isEmpty();
    }

    @Test
    void deuxJoueursPeuventPorterLeMemeRiotIdLeTempsDUnRafraichissement() {
        // L'ancien propriétaire du nom n'a pas encore été rafraîchi
        insertMainPlayer();
        insert(RiotFixtures.puuid(2), RiotFixtures.GAME_NAME, RiotFixtures.TAG_LINE, "EUW1", 30);

        assertThat(findByRiotId("EUW1", RiotFixtures.GAME_NAME, RiotFixtures.TAG_LINE)).hasSize(2);
    }

    @Test
    void laRechercheParRiotIdUtiliseLIndex() {
        insertMainPlayer();
        // La table de test est minuscule : sans cela, PostgreSQL préférerait toujours un parcours complet
        jdbc.sql("SET LOCAL enable_seqscan = off").update();

        String plan = String.join("\n", jdbc.sql("EXPLAIN " + FIND_BY_RIOT_ID)
                .param("platform", "EUW1").param("gameName", "x").param("tagLine", "y")
                .query(String.class).list());

        assertThat(plan).contains("player_riot_id_idx");
    }

    @Test
    void lePuuidEstUnique() {
        insertMainPlayer();

        assertThatThrownBy(() -> insert(RiotFixtures.PUUID, "Autre", "TAG", "EUW1", 1))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @ParameterizedTest
    @EnumSource(Platform.class)
    void accepteToutesLesPlateformes(Platform platform) {
        assertThat(insert(RiotFixtures.PUUID, "Joueur", "TAG", platform.name(), 1)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"euw1", "EUW", "PH2", "Europe Ouest", ""})
    void refuseUnePlateformeQuiNEstPasUnNomDEnum(String platform) {
        assertThatThrownBy(() -> insert(RiotFixtures.PUUID, "Joueur", "TAG", platform, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refuseUnNiveauNegatif() {
        assertThatThrownBy(() -> insert(RiotFixtures.PUUID, "Joueur", "TAG", "EUW1", -1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void leRiotIdEstObligatoire() {
        assertThatThrownBy(() -> insert(RiotFixtures.PUUID, null, "TAG", "EUW1", 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void lesDatesSontStockeesAvecLeFuseauHoraire() {
        String type = jdbc.sql("""
                SELECT data_type FROM information_schema.columns
                WHERE table_name = 'player' AND column_name = 'last_refreshed_at'
                """).query(String.class).single();

        assertThat(type).isEqualTo("timestamp with time zone");
    }
}
