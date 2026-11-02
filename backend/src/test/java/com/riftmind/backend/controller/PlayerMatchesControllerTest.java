package com.riftmind.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.riftmind.backend.dto.ChampionStatsResponse;
import com.riftmind.backend.dto.CompactParticipantResponse;
import com.riftmind.backend.dto.MatchPageResponse;
import com.riftmind.backend.dto.MatchSummaryResponse;
import com.riftmind.backend.dto.ParticipantResponse;
import com.riftmind.backend.exception.InvalidRequestException;
import com.riftmind.backend.exception.PlayerNotFoundException;
import com.riftmind.backend.riot.RiotFixtures;
import com.riftmind.backend.service.MatchQueryService;

/** {@code GET /api/players/{puuid}/matches} (tâche 5.1). Le service est simulé. */
@WebMvcTest(PlayerMatchesController.class)
class PlayerMatchesControllerTest {

    private static final String PUUID = RiotFixtures.PUUID;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MatchQueryService matchQueryService;

    static ParticipantResponse kalista() {
        return new ParticipantResponse(1, PUUID, "Joueur Test", "EUW", 100, 429, "Kalista", 15, "BOTTOM", true, 6, 4,
                3, 250, 13852, 19708, 19, List.of(1086, 3087, 3124, 1082, 3302, 1011, 3363), List.of(4, 21), 8008,
                8400);
    }

    @Test
    void renvoieUnePageDHistorique() throws Exception {
        MatchSummaryResponse match = new MatchSummaryResponse("EUW1_7000000001", 420, "16.21.834.2754",
                Instant.parse("2026-11-01T20:00:00Z"), 1463, false, kalista(),
                List.of(new CompactParticipantResponse(PUUID, "Joueur Test", "EUW", 100, 429, "Kalista")));
        when(matchQueryService.getHistory(PUUID, 0, 10, null))
                .thenReturn(new MatchPageResponse(List.of(match), 0, 10, true));

        mockMvc.perform(get("/api/players/{puuid}/matches", PUUID))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.items[0].matchId").value("EUW1_7000000001"))
                .andExpect(jsonPath("$.items[0].queueId").value(420))
                .andExpect(jsonPath("$.items[0].gameStart").value("2026-11-01T20:00:00Z"))
                .andExpect(jsonPath("$.items[0].durationSeconds").value(1463))
                .andExpect(jsonPath("$.items[0].remake").value(false))
                .andExpect(jsonPath("$.items[0].player.championName").value("Kalista"))
                .andExpect(jsonPath("$.items[0].player.items.length()").value(7))
                .andExpect(jsonPath("$.items[0].player.summonerSpells[1]").value(21))
                .andExpect(jsonPath("$.items[0].participants[0].championId").value(429));
    }

    @Test
    void transmetLaPageLaTailleEtLaFile() throws Exception {
        when(matchQueryService.getHistory(any(), anyInt(), anyInt(), any()))
                .thenReturn(new MatchPageResponse(List.of(), 2, 5, false));

        mockMvc.perform(get("/api/players/{puuid}/matches?page=2&size=5&queue=440", PUUID))
                .andExpect(status().isOk());

        verify(matchQueryService).getHistory(PUUID, 2, 5, 440);
    }

    @Test
    void uneFileNonNumeriqueDonne400() throws Exception {
        mockMvc.perform(get("/api/players/{puuid}/matches?queue=soloq", PUUID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(matchQueryService);
    }

    @Test
    void uneTailleHorsLimitesDonne400() throws Exception {
        when(matchQueryService.getHistory(any(), anyInt(), anyInt(), any()))
                .thenThrow(new InvalidRequestException("La taille de page doit être entre 1 et 50 : 500."));

        mockMvc.perform(get("/api/players/{puuid}/matches?size=500", PUUID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void renvoieLesStatsParChampion() throws Exception {
        when(matchQueryService.getChampionStats(PUUID, 420)).thenReturn(List.of(
                new ChampionStatsResponse(429, "Kalista", 3, 2, 1, 66.67, 6, 3, 3, 3, 10, 19902.67)));

        mockMvc.perform(get("/api/players/{puuid}/champions?queue=420", PUUID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].championId").value(429))
                .andExpect(jsonPath("$[0].championName").value("Kalista"))
                .andExpect(jsonPath("$[0].games").value(3))
                .andExpect(jsonPath("$[0].wins").value(2))
                .andExpect(jsonPath("$[0].losses").value(1))
                .andExpect(jsonPath("$[0].winrate").value(66.67))
                .andExpect(jsonPath("$[0].kda").value(3.0))
                .andExpect(jsonPath("$[0].csPerMinute").value(10.0))
                .andExpect(jsonPath("$[0].avgDamage").value(19902.67));
    }

    @Test
    void sansFileLesStatsPortentSurToutesLesFiles() throws Exception {
        mockMvc.perform(get("/api/players/{puuid}/champions", PUUID)).andExpect(status().isOk());

        verify(matchQueryService).getChampionStats(PUUID, null);
    }

    @Test
    void unJoueurInconnuDonne404() throws Exception {
        when(matchQueryService.getHistory(any(), anyInt(), anyInt(), any()))
                .thenThrow(PlayerNotFoundException.unknownPuuid("inconnu"));

        mockMvc.perform(get("/api/players/inconnu/matches"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
