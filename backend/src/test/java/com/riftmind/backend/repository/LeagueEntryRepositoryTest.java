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
import com.riftmind.backend.model.LeagueEntry;
import com.riftmind.backend.model.Player;
import com.riftmind.backend.riot.Platform;
import com.riftmind.backend.riot.RankedQueue;
import com.riftmind.backend.riot.RiotFixtures;

/**
 * Entité {@link LeagueEntry} et {@link LeagueEntryRepository} sur un vrai PostgreSQL (tâche 3.2).
 * Hibernate démarre en {@code ddl-auto=validate} : si l'entité ne correspond pas à la table, le test échoue.
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import(TestcontainersConfiguration.class)
class LeagueEntryRepositoryTest {

    /** Précision de PostgreSQL : la microseconde. */
    private static final Instant FETCHED_AT = Instant.parse("2026-11-01T20:00:00.123456Z");

    @Autowired
    private LeagueEntryRepository repository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        statistics = entityManager.getEntityManager().getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        playerRepository.save(new Player(RiotFixtures.PUUID, RiotFixtures.GAME_NAME, RiotFixtures.TAG_LINE,
                Platform.EUW1, 512, 7, FETCHED_AT));
        entityManager.flush();
    }

    private static LeagueEntry soloQ() {
        return new LeagueEntry(RiotFixtures.PUUID, RankedQueue.RANKED_SOLO_5x5, "CHALLENGER", "I", 1234, 250, 200,
                false, FETCHED_AT);
    }

    private static LeagueEntry flex() {
        return new LeagueEntry(RiotFixtures.PUUID, RankedQueue.RANKED_FLEX_SR, "GOLD", "II", 45, 12, 10, true,
                FETCHED_AT);
    }

    /** Enregistre en base puis vide le cache JPA : la lecture suivante vient vraiment de PostgreSQL. */
    private void persistAndClear(LeagueEntry... entries) {
        repository.saveAll(List.of(entries));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void enregistreEtRelitUnRang() {
        persistAndClear(soloQ());

        LeagueEntry found = repository.findById(new LeagueEntry.Key(RiotFixtures.PUUID, RankedQueue.RANKED_SOLO_5x5))
                .orElseThrow();

        assertThat(found.getQueueType()).isEqualTo(RankedQueue.RANKED_SOLO_5x5);
        assertThat(found.getTier()).isEqualTo("CHALLENGER");
        assertThat(found.getRank()).isEqualTo("I");
        assertThat(found.getLeaguePoints()).isEqualTo(1234);
        assertThat(found.getWins()).isEqualTo(250);
        assertThat(found.getLosses()).isEqualTo(200);
        assertThat(found.isHotStreak()).isFalse();
        assertThat(found.getUpdatedAt()).isEqualTo(FETCHED_AT);
        assertThat(found.isNew()).isFalse();
    }

    @Test
    void laFileEstStockeeAvecLeNomDeLEnum() {
        persistAndClear(soloQ());

        Object stored = entityManager.getEntityManager()
                .createNativeQuery("SELECT queue_type FROM league_entry WHERE puuid = ?1")
                .setParameter(1, RiotFixtures.PUUID).getSingleResult();

        assertThat(stored).isEqualTo("RANKED_SOLO_5x5");
    }

    @Test
    void trouveTousLesRangsDUnJoueur() {
        persistAndClear(soloQ(), flex());

        assertThat(repository.findByPuuid(RiotFixtures.PUUID))
                .extracting(LeagueEntry::getQueueType)
                .containsExactlyInAnyOrder(RankedQueue.RANKED_SOLO_5x5, RankedQueue.RANKED_FLEX_SR);
        assertThat(repository.findByPuuid(RiotFixtures.puuid(2))).isEmpty();
    }

    @Test
    void unNouveauRangEstInsereSansSelectPrealable() {
        LeagueEntry entry = soloQ();
        statistics.clear();

        repository.save(entry);
        entityManager.flush();

        // Un seul ordre SQL, l'INSERT. Sans Persistable, Spring Data ferait d'abord un SELECT (clé non générée).
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(statistics.getEntityInsertCount()).isEqualTo(1);
        assertThat(entry.isNew()).isFalse();
    }

    @Test
    void uneMiseAJourRemplaceLeRang() {
        persistAndClear(soloQ());
        Instant later = FETCHED_AT.plusSeconds(3600);

        LeagueEntry entry = repository.findByPuuid(RiotFixtures.PUUID).getFirst();
        entry.update("GRANDMASTER", "I", 980, 251, 210, true, later);
        persistAndClear(entry);

        LeagueEntry reloaded = repository.findByPuuid(RiotFixtures.PUUID).getFirst();
        assertThat(reloaded.getTier()).isEqualTo("GRANDMASTER");
        assertThat(reloaded.getLeaguePoints()).isEqualTo(980);
        assertThat(reloaded.getWins()).isEqualTo(251);
        assertThat(reloaded.getLosses()).isEqualTo(210);
        assertThat(reloaded.isHotStreak()).isTrue();
        assertThat(reloaded.getUpdatedAt()).isEqualTo(later);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void deuxRangsDuMemeJoueurDansLaMemeFileSontEgaux() {
        LeagueEntry other = new LeagueEntry(RiotFixtures.PUUID, RankedQueue.RANKED_SOLO_5x5, "IRON", "IV", 0, 0, 1,
                false, FETCHED_AT);

        assertThat(soloQ()).isEqualTo(other).hasSameHashCodeAs(other);
        assertThat(soloQ()).isNotEqualTo(flex());
    }
}
