package com.riftmind.backend.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.riftmind.backend.dto.CompactParticipantResponse;
import com.riftmind.backend.dto.MatchDetailResponse;
import com.riftmind.backend.dto.MatchPageResponse;
import com.riftmind.backend.dto.MatchSummaryResponse;
import com.riftmind.backend.dto.ParticipantResponse;
import com.riftmind.backend.exception.InvalidRequestException;
import com.riftmind.backend.exception.MatchNotFoundException;
import com.riftmind.backend.exception.PlayerNotFoundException;
import com.riftmind.backend.model.MatchHeader;
import com.riftmind.backend.model.MatchParticipant;
import com.riftmind.backend.repository.MatchParticipantRepository;
import com.riftmind.backend.repository.MatchRepository;
import com.riftmind.backend.repository.PlayerRepository;

/**
 * Lecture des parties enregistrées (phase 5) : uniquement depuis la base, sans jamais appeler Riot. La réponse brute
 * des matchs ({@code raw}) n'est jamais chargée.
 */
@Service
@Transactional(readOnly = true)
public class MatchQueryService {

    /** Taille de page par défaut de l'historique. */
    public static final int DEFAULT_PAGE_SIZE = 10;
    /** Taille de page maximale de l'historique. */
    public static final int MAX_PAGE_SIZE = 50;

    private final PlayerRepository playerRepository;
    private final MatchRepository matchRepository;
    private final MatchParticipantRepository participantRepository;

    public MatchQueryService(PlayerRepository playerRepository, MatchRepository matchRepository,
            MatchParticipantRepository participantRepository) {
        this.playerRepository = playerRepository;
        this.matchRepository = matchRepository;
        this.participantRepository = participantRepository;
    }

    /**
     * Historique d'un joueur, de la partie la plus récente à la plus ancienne (tâche 5.1). Trois requêtes par page :
     * les participations du joueur, les en-têtes des parties, puis tous leurs joueurs.
     *
     * <p>Les parties personnalisées n'y apparaissent jamais : elles sont enregistrées sans joueurs (tâche 4.6). Les
     * remakes y sont, marqués {@code remake}.
     *
     * @param page    numéro de page, à partir de 0
     * @param size    nombre de parties par page, de 1 à {@value #MAX_PAGE_SIZE}
     * @param queueId file de jeu (ex. 420 = SoloQ), ou {@code null} pour toutes
     * @throws PlayerNotFoundException si aucun joueur n'a ce PUUID
     * @throws InvalidRequestException si la page ou la taille sont hors limites
     */
    public MatchPageResponse getHistory(String puuid, int page, int size, Integer queueId) {
        if (page < 0) {
            throw new InvalidRequestException("La page doit être positive ou nulle : " + page + ".");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("La taille de page doit être entre 1 et " + MAX_PAGE_SIZE + " : "
                    + size + ".");
        }
        requireKnownPlayer(puuid);

        PageRequest pageable = PageRequest.of(page, size);
        Slice<MatchParticipant> mine = queueId == null
                ? participantRepository.findByPuuidOrderByGameStartDescMatchIdDesc(puuid, pageable)
                : participantRepository.findByPuuidAndQueueIdOrderByGameStartDescMatchIdDesc(puuid, queueId,
                        pageable);
        List<String> matchIds = mine.stream().map(MatchParticipant::getMatchId).toList();
        if (matchIds.isEmpty()) {
            return new MatchPageResponse(List.of(), page, size, false);
        }

        Map<String, MatchHeader> headers = matchRepository.findHeaders(matchIds).stream()
                .collect(Collectors.toMap(MatchHeader::matchId, Function.identity()));
        Map<String, List<MatchParticipant>> playersByMatch = participantRepository
                .findByMatchIdInOrderByMatchIdAscParticipantIdAsc(matchIds).stream()
                .collect(Collectors.groupingBy(MatchParticipant::getMatchId));

        List<MatchSummaryResponse> items = mine.stream()
                .map(me -> summary(headers.get(me.getMatchId()), me, playersByMatch.get(me.getMatchId())))
                .toList();
        return new MatchPageResponse(items, page, size, mine.hasNext());
    }

    /**
     * Détail complet d'une partie : ses 10 joueurs et les totaux par équipe (tâche 5.2).
     *
     * @throws MatchNotFoundException si la partie n'est pas en base, ou si c'est une partie personnalisée (enregistrée
     *                                sans joueurs, que Riot interdit d'afficher)
     */
    public MatchDetailResponse getMatch(String matchId) {
        MatchHeader header = matchRepository.findHeader(matchId)
                .orElseThrow(() -> new MatchNotFoundException(matchId));
        List<ParticipantResponse> players = participantRepository.findByMatchIdOrderByParticipantId(matchId).stream()
                .map(ParticipantResponse::from)
                .toList();
        if (players.isEmpty()) {
            throw new MatchNotFoundException(matchId);
        }
        return MatchDetailResponse.of(header, players);
    }

    private static MatchSummaryResponse summary(MatchHeader header, MatchParticipant me,
            List<MatchParticipant> players) {
        return new MatchSummaryResponse(header.matchId(), header.queueId(), header.gameVersion(), header.gameStart(),
                header.durationSeconds(), header.remake(), ParticipantResponse.from(me),
                players.stream().map(CompactParticipantResponse::from).toList());
    }

    private void requireKnownPlayer(String puuid) {
        if (!playerRepository.existsById(puuid)) {
            throw PlayerNotFoundException.unknownPuuid(puuid);
        }
    }
}
