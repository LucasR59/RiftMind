package com.riftmind.backend.service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import org.springframework.stereotype.Service;

import com.riftmind.backend.dto.RankHistoryResponse;
import com.riftmind.backend.dto.RankPointResponse;
import com.riftmind.backend.exception.PlayerNotFoundException;
import com.riftmind.backend.model.RankSnapshot;
import com.riftmind.backend.repository.PlayerRepository;
import com.riftmind.backend.repository.RankSnapshotRepository;
import com.riftmind.backend.riot.RankedQueue;

/**
 * Évolution du rang d'un joueur et son peak (tâche 7.10), lus en base sans appeler Riot.
 *
 * <p>Riot ne fournit ni le MMR ni les rangs passés : l'historique est fait des rangs que RiftMind a vus passer
 * ({@code rank_snapshot}, enregistrés par {@link LeagueService}), et le peak est le meilleur d'entre eux. Un rang
 * dont le palier est inconnu (ajouté par Riot depuis) n'a pas de score : il est ignoré.
 */
@Service
public class RankHistoryService {

    /** Points renvoyés au plus par file (les plus récents) : largement assez pour une saison. */
    public static final int MAX_POINTS = 500;

    private final PlayerRepository playerRepository;
    private final RankSnapshotRepository rankSnapshotRepository;

    public RankHistoryService(PlayerRepository playerRepository, RankSnapshotRepository rankSnapshotRepository) {
        this.playerRepository = playerRepository;
        this.rankSnapshotRepository = rankSnapshotRepository;
    }

    /**
     * @return une entrée par file ayant au moins un rang enregistré, SoloQ puis Flex ; liste vide si le joueur n'a
     *         jamais été classé depuis que RiftMind le suit
     * @throws PlayerNotFoundException si aucun joueur n'a ce PUUID
     */
    public List<RankHistoryResponse> getHistory(String puuid) {
        if (!playerRepository.existsById(puuid)) {
            throw PlayerNotFoundException.unknownPuuid(puuid);
        }
        Map<RankedQueue, List<RankPointResponse>> byQueue = new EnumMap<>(RankedQueue.class);
        for (RankSnapshot snapshot : rankSnapshotRepository.findByPuuidOrderByRecordedAtAscIdAsc(puuid)) {
            OptionalInt score = snapshot.score();
            if (score.isPresent()) {
                byQueue.computeIfAbsent(snapshot.getQueueType(), queue -> new ArrayList<>())
                        .add(RankPointResponse.from(snapshot, score.getAsInt()));
            }
        }
        List<RankHistoryResponse> history = new ArrayList<>();
        byQueue.forEach((queue, points) -> history.add(new RankHistoryResponse(queue.name(),
                points.subList(Math.max(0, points.size() - MAX_POINTS), points.size()), peak(points))));
        return history;
    }

    /** Meilleur score ; en cas d'égalité, le premier atteint. */
    private static RankPointResponse peak(List<RankPointResponse> points) {
        RankPointResponse peak = points.getFirst();
        for (RankPointResponse point : points) {
            if (point.score() > peak.score()) {
                peak = point;
            }
        }
        return peak;
    }
}
