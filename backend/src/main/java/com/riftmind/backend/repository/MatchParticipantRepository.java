package com.riftmind.backend.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.riftmind.backend.model.ChampionStatsRow;
import com.riftmind.backend.model.MatchParticipant;

/** Accès à la table {@code match_participant}. La clé est le couple (match, numéro du participant). */
public interface MatchParticipantRepository extends JpaRepository<MatchParticipant, MatchParticipant.Key> {

    /**
     * Supprime toutes les participations de ce joueur (suppression des données, tâche 5.8), en une requête.
     *
     * @return nombre de lignes supprimées
     */
    @Modifying
    @Query("delete from MatchParticipant p where p.puuid = :puuid")
    int deleteAllByPuuid(String puuid);

    /** Les joueurs d'une partie, dans l'ordre de Riot (équipe bleue puis rouge). */
    List<MatchParticipant> findByMatchIdOrderByParticipantId(String matchId);

    /** Les joueurs de plusieurs parties, groupés par partie puis dans l'ordre de Riot. */
    List<MatchParticipant> findByMatchIdInOrderByMatchIdAscParticipantIdAsc(Collection<String> matchIds);

    /**
     * Participations d'un joueur, de la plus récente à la plus ancienne (index
     * {@code match_participant_puuid_game_start_idx}). Une {@link Slice} lit une ligne de plus que la page pour savoir
     * s'il en reste, sans compter toutes les parties.
     */
    Slice<MatchParticipant> findByPuuidOrderByGameStartDescMatchIdDesc(String puuid, Pageable pageable);

    /** Comme {@link #findByPuuidOrderByGameStartDescMatchIdDesc}, limité à une file de jeu. */
    Slice<MatchParticipant> findByPuuidAndQueueIdOrderByGameStartDescMatchIdDesc(String puuid, int queueId,
            Pageable pageable);

    /**
     * Totaux du joueur par champion, hors remakes, du plus joué au moins joué (index
     * {@code match_participant_puuid_champion_idx}). Les parties personnalisées n'ont pas de joueurs : elles ne
     * comptent jamais.
     *
     * @param queueId file de jeu, ou {@code null} pour toutes
     */
    @Query("""
            select new com.riftmind.backend.model.ChampionStatsRow(
                p.championId, max(p.championName), count(p),
                sum(case when p.win = true then 1 else 0 end),
                sum(p.kills), sum(p.deaths), sum(p.assists), sum(p.cs), sum(m.durationSeconds),
                sum(p.damageChampions))
            from MatchParticipant p join Match m on m.matchId = p.matchId
            where p.puuid = :puuid and m.remake = false and (:queueId is null or p.queueId = :queueId)
            group by p.championId
            order by count(p) desc, max(p.championName)
            """)
    List<ChampionStatsRow> findChampionStats(String puuid, Integer queueId);

    /** Dernières participations du joueur hors remakes, de la plus récente à la plus ancienne (toutes files). */
    @Query("""
            select p from MatchParticipant p join Match m on m.matchId = p.matchId
            where p.puuid = :puuid and m.remake = false
            order by p.gameStart desc, p.matchId desc
            """)
    List<MatchParticipant> findRecentWithoutRemakes(String puuid, Pageable pageable);
}
