package com.riftmind.backend.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import com.riftmind.backend.model.RankSnapshot;
import com.riftmind.backend.model.RankedGame;
import com.riftmind.backend.riot.RankedQueue;

/**
 * Gain ou perte de LP de chaque partie classée, quand on peut le savoir avec certitude.
 *
 * <p>Riot ne donne pas les LP gagnés ou perdus par partie (Match-V5 n'a aucun champ de rang). RiftMind relève le rang
 * à chaque recherche ou mise à jour ({@code rank_snapshot}), avec le bilan victoires/défaites. Entre deux relevés
 * successifs d'une même file, la variation de LP est attribuée à une partie <b>seulement si</b> :
 * <ul>
 *   <li>le bilan a augmenté d'exactement une partie (une victoire, ou une défaite) ;</li>
 *   <li>exactement une partie de cette file, hors remakes, s'est terminée entre les deux relevés, et elle est en
 *       base ;</li>
 *   <li>son résultat correspond : victoire si le bilan a gagné une victoire, défaite sinon.</li>
 * </ul>
 * Sinon (plusieurs parties entre deux relevés, partie absente de la base, nouvelle saison…), rien n'est attribué :
 * jamais d'estimation. La variation est la différence de score ({@code RankScore}), qui tient compte des changements
 * de division et de palier : Diamant I 90 LP → Maître 10 LP = +20.
 */
final class LpChanges {

    private static final Comparator<RankSnapshot> CHRONOLOGICAL = Comparator.comparing(RankSnapshot::getRecordedAt)
            .thenComparing(RankSnapshot::getId, Comparator.nullsFirst(Comparator.naturalOrder()));

    private LpChanges() {
    }

    /**
     * @param snapshots rangs relevés du joueur, toutes files, dans n'importe quel ordre
     * @param games     parties classées du joueur couvrant la période des relevés, remakes compris
     * @return la variation de LP par identifiant de partie, pour les parties où elle est certaine
     */
    static Map<String, Integer> attribute(List<RankSnapshot> snapshots, List<RankedGame> games) {
        Map<String, Integer> changes = new HashMap<>();
        for (RankedQueue queue : RankedQueue.values()) {
            List<RankSnapshot> history = snapshots.stream()
                    .filter(snapshot -> snapshot.getQueueType() == queue)
                    .sorted(CHRONOLOGICAL)
                    .toList();
            List<RankedGame> queueGames = games.stream()
                    .filter(game -> game.queueId() == queue.queueId() && !game.remake())
                    .toList();
            for (int i = 1; i < history.size(); i++) {
                attributePair(history.get(i - 1), history.get(i), queueGames, changes);
            }
        }
        return changes;
    }

    private static void attributePair(RankSnapshot before, RankSnapshot after, List<RankedGame> games,
            Map<String, Integer> changes) {
        int wins = after.getWins() - before.getWins();
        int losses = after.getLosses() - before.getLosses();
        // Exactement une partie jouée (un bilan qui baisse = nouvelle saison : rien à attribuer)
        if (wins < 0 || losses < 0 || wins + losses != 1) {
            return;
        }
        OptionalInt scoreBefore = before.score();
        OptionalInt scoreAfter = after.score();
        if (scoreBefore.isEmpty() || scoreAfter.isEmpty()) {
            return;
        }
        List<RankedGame> between = games.stream()
                .filter(game -> game.gameEnd().isAfter(before.getRecordedAt())
                        && !game.gameEnd().isAfter(after.getRecordedAt()))
                .toList();
        if (between.size() != 1 || between.getFirst().win() != (wins == 1)) {
            return;
        }
        changes.put(between.getFirst().matchId(), scoreAfter.getAsInt() - scoreBefore.getAsInt());
    }
}
