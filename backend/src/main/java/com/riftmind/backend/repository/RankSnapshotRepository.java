package com.riftmind.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.riftmind.backend.model.RankSnapshot;
import com.riftmind.backend.riot.RankedQueue;

/** Accès à la table {@code rank_snapshot} (index {@code (puuid, queue_type, recorded_at)}). */
public interface RankSnapshotRepository extends JpaRepository<RankSnapshot, Long> {

    /** Historique complet d'un joueur, toutes files, du plus ancien au plus récent. */
    List<RankSnapshot> findByPuuidOrderByRecordedAtAscIdAsc(String puuid);

    /** Dernier rang enregistré d'un joueur dans une file. */
    Optional<RankSnapshot> findFirstByPuuidAndQueueTypeOrderByRecordedAtDescIdDesc(String puuid,
            RankedQueue queueType);
}
