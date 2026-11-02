package com.riftmind.backend.controller;

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

import com.riftmind.backend.dto.MatchDetailResponse;
import com.riftmind.backend.dto.TeamResponse;
import com.riftmind.backend.exception.MatchNotFoundException;
import com.riftmind.backend.service.MatchQueryService;

/** {@code GET /api/matches/{matchId}} (tâche 5.2). Le service est simulé. */
@WebMvcTest(MatchController.class)
class MatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MatchQueryService matchQueryService;

    @Test
    void renvoieLeDetailDUnePartie() throws Exception {
        when(matchQueryService.getMatch("EUW1_7000000001")).thenReturn(new MatchDetailResponse("EUW1_7000000001",
                "EUW1", 420, "16.21.834.2754", Instant.parse("2026-11-01T20:00:00Z"), 1463, false,
                List.of(new TeamResponse(100, true, 28, 17, 35, 58983, 89492),
                        new TeamResponse(200, false, 17, 28, 13, 49001, 61573)),
                List.of(PlayerMatchesControllerTest.kalista())));

        mockMvc.perform(get("/api/matches/EUW1_7000000001"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.matchId").value("EUW1_7000000001"))
                .andExpect(jsonPath("$.platform").value("EUW1"))
                .andExpect(jsonPath("$.queueId").value(420))
                .andExpect(jsonPath("$.teams[0].teamId").value(100))
                .andExpect(jsonPath("$.teams[0].win").value(true))
                .andExpect(jsonPath("$.teams[1].gold").value(49001))
                .andExpect(jsonPath("$.participants[0].championName").value("Kalista"))
                .andExpect(jsonPath("$.participants[0].visionScore").value(19))
                .andExpect(jsonPath("$.participants[0].damageChampions").value(19708));
    }

    @Test
    void unePartieInconnueDonne404() throws Exception {
        when(matchQueryService.getMatch("EUW1_404")).thenThrow(new MatchNotFoundException("EUW1_404"));

        mockMvc.perform(get("/api/matches/EUW1_404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Partie « EUW1_404 » introuvable."));
    }
}
