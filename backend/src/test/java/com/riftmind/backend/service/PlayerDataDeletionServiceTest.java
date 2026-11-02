package com.riftmind.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.riftmind.backend.TestcontainersConfiguration;
import com.riftmind.backend.model.LeagueEntry;
import com.riftmind.backend.model.Match;
import com.riftmind.backend.model.MatchParticipant;
import com.riftmind.backend.model.Player;
import com.riftmind.backend.repository.LeagueEntryRepository;
import com.riftmind.backend.repository.MatchParticipantRepository;
import com.riftmind.backend.repository.MatchRepository;
import com.riftmind.backend.repository.PlayerRepository;
import com.riftmind.backend.riot.Platform;
import com.riftmind.backend.riot.RankedQueue;
import com.riftmind.backend.riot.RiotFixtures;
import com.riftmind.backend.riot.dto.MatchDto;

import tools.jackson.databind.json.JsonMapper;

/**
 * Suppression de toutes les données d'un joueur (tâche 5.8), sur un vrai PostgreSQL, avec de vraies parties
 * ({@code match.json}). Le résultat est vérifié en SQL, table par table, réponse brute comprise.
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, PlayerDataDeletionService.class})
class PlayerDataDeletionServiceTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Instant T0 = Instant.parse("2026-11-02T10:00:00Z");
    /** Un autre joueur recherché sur RiftMind, coéquipier du premier dans les parties de test. */
    private static final String OTHER = RiotFixtures.puuid(2);

    @Autowired
    private PlayerDataDeletionService service;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private LeagueEntryRepository leagueEntryRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private MatchParticipantRepository participantRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private DataSource dataSource;

    private JdbcClient jdbc;

    @BeforeEach
    void setUp() {
        jdbc = JdbcClient.create(dataSource);
        for (String puuid : List.of(RiotFixtures.PUUID, OTHER)) {
            playerRepository.save(new Player(puuid, "Joueur", "EUW", Platform.EUW1, 100, 1, T0));
            leagueEntryRepository.save(new LeagueEntry(puuid, RankedQueue.RANKED_SOLO_5x5, "GOLD", "I", 10, 5, 5,
                    false, T0));
        }
        // Deux parties du joueur, et une sans lui (son PUUID remplacé par celui d'un inconnu)
        saveMatch("EUW1_7000000001", RiotFixtures.read(RiotFixtures.MATCH));
        saveMatch("EUW1_7000000002", RiotFixtures.read(RiotFixtures.MATCH));
        saveMatch("EUW1_7000000003", RiotFixtures.read(RiotFixtures.MATCH)
                .replace(RiotFixtures.PUUID, RiotFixtures.puuid(11)));
        entityManager.flush();
        entityManager.clear();
    }

    private void saveMatch(String matchId, String json) {
        String raw = json.replace(RiotFixtures.MATCH_ID, matchId);
        MatchDto dto = JSON.readValue(raw, MatchDto.class);
        Match match = matchRepository.save(new Match(matchId, "EUW1", 420, "16.21", T0, 1463, false, raw, T0));
        participantRepository.saveAll(dto.info().participants().stream()
                .map(p -> MatchParticipant.of(match, p)).toList());
    }

    /** Lignes qui mentionnent ce PUUID, dans toutes les tables, réponse brute des matchs comprise. */
    private long rowsMentioning(String puuid) {
        return jdbc.sql("""
                SELECT (SELECT count(*) FROM player WHERE puuid = :puuid)
                     + (SELECT count(*) FROM league_entry WHERE puuid = :puuid)
                     + (SELECT count(*) FROM match_participant WHERE puuid = :puuid)
                     + (SELECT count(*) FROM match WHERE raw::text LIKE '%' || :puuid || '%')
                """).param("puuid", puuid).query(Long.class).single();
    }

    @Test
    void ilNeResteRienEnBasePourCePuuid() {
        assertThat(rowsMentioning(RiotFixtures.PUUID)).as("avant").isEqualTo(1 + 1 + 2 + 2);

        PlayerDataDeletionService.DeletionResult result = service.deleteAllData(RiotFixtures.PUUID);

        assertThat(rowsMentioning(RiotFixtures.PUUID)).isZero();
        assertThat(result).isEqualTo(new PlayerDataDeletionService.DeletionResult(1, 2, 2));
    }

    @Test
    void lesPartiesEtLesAutresJoueursSontGardes() {
        service.deleteAllData(RiotFixtures.PUUID);

        assertThat(matchRepository.count()).isEqualTo(3);
        assertThat(participantRepository.findByMatchIdOrderByParticipantId("EUW1_7000000001")).hasSize(9);
        assertThat(participantRepository.findByMatchIdOrderByParticipantId("EUW1_7000000003")).hasSize(10);
        // La partie sans le joueur garde sa réponse brute
        assertThat(matchRepository.findById("EUW1_7000000003").orElseThrow().getRaw()).isNotNull();
        // Le coéquipier, lui aussi recherché sur RiftMind, garde tout
        assertThat(playerRepository.existsById(OTHER)).isTrue();
        assertThat(leagueEntryRepository.findByPuuid(OTHER)).hasSize(1);
        assertThat(jdbc.sql("SELECT count(*) FROM match_participant WHERE puuid = :puuid").param("puuid", OTHER)
                .query(Long.class).single()).isEqualTo(3);
    }

    @Test
    void unPuuidInconnuNeModifieRien() {
        long participations = participantRepository.count();

        assertThat(service.deleteAllData(RiotFixtures.puuid(42)))
                .isEqualTo(new PlayerDataDeletionService.DeletionResult(0, 0, 0));
        assertThat(participantRepository.count()).isEqualTo(participations);
        assertThat(playerRepository.count()).isEqualTo(2);
    }

    @Test
    void unJoueurJamaisRechercheMaisPresentDansDesPartiesEstAussiEfface() {
        // puuid(3) joue dans les parties mais n'a jamais été recherché : pas de ligne player
        String teammate = RiotFixtures.puuid(3);

        PlayerDataDeletionService.DeletionResult result = service.deleteAllData(teammate);

        assertThat(result).isEqualTo(new PlayerDataDeletionService.DeletionResult(0, 3, 3));
        assertThat(rowsMentioning(teammate)).isZero();
    }

    @Test
    void traiteUneListeDePuuidTransmiseParRiot() {
        PlayerDataDeletionService.DeletionResult result = service.deleteAllData(
                List.of(RiotFixtures.PUUID, OTHER, RiotFixtures.PUUID, RiotFixtures.puuid(42)));

        assertThat(rowsMentioning(RiotFixtures.PUUID)).isZero();
        assertThat(rowsMentioning(OTHER)).isZero();
        assertThat(result.players()).isEqualTo(2);
        assertThat(playerRepository.count()).isZero();
    }
}
