package com.riftmind.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.riftmind.backend.TestcontainersConfiguration;
import com.riftmind.backend.model.Match;
import com.riftmind.backend.model.MatchParticipant;
import com.riftmind.backend.riot.RiotFixtures;
import com.riftmind.backend.riot.dto.InfoDto;
import com.riftmind.backend.riot.dto.MatchDto;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Entités {@link Match} et {@link MatchParticipant}, et leurs repositories, sur un vrai PostgreSQL (tâche 4.3), à
 * partir du vrai match de {@code match.json}. Hibernate démarre en {@code ddl-auto=validate} : si une entité ne
 * correspond pas à sa table, le test échoue.
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import(TestcontainersConfiguration.class)
class MatchRepositoryTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Instant SAVED_AT = Instant.parse("2026-11-02T10:00:00.123456Z");
    private static final String RAW = RiotFixtures.read(RiotFixtures.MATCH);

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private MatchParticipantRepository participantRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        statistics = entityManager.getEntityManager().getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
    }

    private static Match match(String matchId, InfoDto info, String raw) {
        return new Match(matchId, info.platformId(), info.queueId(), info.gameVersion(), info.gameStart(),
                Math.toIntExact(info.durationSeconds()), false, raw, SAVED_AT);
    }

    /** Enregistre le match du fichier de test et ses 10 joueurs, puis vide le cache JPA. */
    private Match saveFixtureMatch() {
        MatchDto dto = JSON.readValue(RAW, MatchDto.class);
        Match match = matchRepository.save(match(dto.metadata().matchId(), dto.info(), RAW));
        participantRepository.saveAll(dto.info().participants().stream()
                .map(p -> MatchParticipant.of(match, p)).toList());
        entityManager.flush();
        entityManager.clear();
        return match;
    }

    @Test
    void enregistreEtRelitUnMatch() {
        saveFixtureMatch();

        Match found = matchRepository.findById(RiotFixtures.MATCH_ID).orElseThrow();

        assertThat(found.getPlatform()).isEqualTo("EUW1");
        assertThat(found.getQueueId()).isEqualTo(420);
        assertThat(found.getGameVersion()).isEqualTo("16.21.834.2754");
        assertThat(found.getGameStart()).isEqualTo(Instant.ofEpochMilli(1790951450908L));
        assertThat(found.getDurationSeconds()).isEqualTo(1463);
        assertThat(found.isRemake()).isFalse();
        assertThat(found.getCreatedAt()).isEqualTo(SAVED_AT);
        assertThat(found.isNew()).isFalse();
    }

    @Test
    void laReponseBruteEstStockeeEnJsonbEtRelueIntacte() {
        saveFixtureMatch();

        Match found = matchRepository.findById(RiotFixtures.MATCH_ID).orElseThrow();
        // jsonb normalise les espaces et l'ordre des clés : on compare le contenu JSON, pas le texte
        JsonNode reread = JSON.readTree(found.getRaw());
        assertThat(reread).isEqualTo(JSON.readTree(RAW));
        // Interrogeable en SQL
        Object queue = entityManager.getEntityManager()
                .createNativeQuery("SELECT raw -> 'info' ->> 'gameMode' FROM match WHERE match_id = ?1")
                .setParameter(1, RiotFixtures.MATCH_ID).getSingleResult();
        assertThat(queue).isEqualTo("CLASSIC");
    }

    @Test
    void unMatchSansReponseBrute() {
        MatchDto dto = JSON.readValue(RAW, MatchDto.class);
        matchRepository.save(match("EUW1_7000000099", dto.info(), null));
        entityManager.flush();
        entityManager.clear();

        assertThat(matchRepository.findById("EUW1_7000000099").orElseThrow().getRaw()).isNull();
    }

    @Test
    void enregistreEtRelitLesJoueursDUnMatch() {
        saveFixtureMatch();

        List<MatchParticipant> participants =
                participantRepository.findByMatchIdOrderByParticipantId(RiotFixtures.MATCH_ID);

        assertThat(participants).hasSize(10).extracting(MatchParticipant::getParticipantId)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        MatchParticipant me = participants.get(3);
        assertThat(me.getPuuid()).isEqualTo(RiotFixtures.PUUID);
        assertThat(me.getGameName()).isEqualTo("Joueur Test");
        assertThat(me.getTagLine()).isEqualTo("EUW");
        assertThat(me.getTeamId()).isEqualTo(100);
        assertThat(me.getChampionId()).isEqualTo(429);
        assertThat(me.getChampionName()).isEqualTo("Kalista");
        assertThat(me.getChampLevel()).isEqualTo(15);
        assertThat(me.getPosition()).isEqualTo("BOTTOM");
        assertThat(me.isWin()).isTrue();
        assertThat(me.getKills()).isEqualTo(6);
        assertThat(me.getDeaths()).isEqualTo(4);
        assertThat(me.getAssists()).isEqualTo(3);
        assertThat(me.getCs()).isEqualTo(250);
        assertThat(me.getGold()).isEqualTo(13852);
        assertThat(me.getDamageChampions()).isEqualTo(19708);
        assertThat(me.getVisionScore()).isEqualTo(19);
        assertThat(me.getItems()).containsExactly(1086, 3087, 3124, 1082, 3302, 1011, 3363);
        assertThat(me.getSummonerSpells()).containsExactly(4, 21);
        assertThat(me.getKeystone()).isEqualTo(8008);
        assertThat(me.getSecondaryStyle()).isEqualTo(8400);
        // Recopiés depuis le match
        assertThat(me.getGameStart()).isEqualTo(Instant.ofEpochMilli(1790951450908L));
        assertThat(me.getQueueId()).isEqualTo(420);
    }

    @Test
    void trouveLesMatchsDejaEnBase() {
        saveFixtureMatch();

        assertThat(matchRepository.findExistingIds(List.of(RiotFixtures.MATCH_ID, "EUW1_7000000002")))
                .containsExactly(RiotFixtures.MATCH_ID);
        assertThat(matchRepository.findExistingIds(List.of("EUW1_7000000002"))).isEmpty();
    }

    @Test
    void unNouveauMatchEstInsereSansSelectPrealable() {
        MatchDto dto = JSON.readValue(RAW, MatchDto.class);
        statistics.clear();

        Match match = matchRepository.save(match(RiotFixtures.MATCH_ID, dto.info(), RAW));
        participantRepository.saveAll(dto.info().participants().stream()
                .map(p -> MatchParticipant.of(match, p)).toList());
        entityManager.flush();

        // 1 INSERT pour le match + 10 pour les joueurs, aucun SELECT (clés fournies, et non générées)
        assertThat(statistics.getEntityInsertCount()).isEqualTo(11);
        assertThat(statistics.getEntityLoadCount()).isZero();
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(11);
    }

    @Test
    void lesObjetsRenvoyesSontDesCopies() {
        MatchDto dto = JSON.readValue(RAW, MatchDto.class);
        MatchParticipant p = MatchParticipant.of(match(RiotFixtures.MATCH_ID, dto.info(), null),
                dto.info().participants().getFirst());

        p.getItems()[0] = -1;

        assertThat(p.getItems()[0]).isNotEqualTo(-1);
    }
}
