package com.riftmind.backend.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.riftmind.backend.dto.RankHistoryResponse;
import com.riftmind.backend.dto.RankPointResponse;
import com.riftmind.backend.exception.PlayerNotFoundException;
import com.riftmind.backend.riot.RiotFixtures;
import com.riftmind.backend.service.RankHistoryService;

/** {@code GET /api/players/{puuid}/rank-history} (tâche 7.10) : format JSON et 404. */
@WebMvcTest(RankHistoryController.class)
class RankHistoryControllerTest {

    private static final String URL = "/api/players/" + RiotFixtures.PUUID + "/rank-history";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RankHistoryService service;

    @Test
    void renvoieLesPointsEtLePeakDeChaqueFile() throws Exception {
        RankPointResponse gold = new RankPointResponse(Instant.parse("2026-10-01T18:00:00Z"), "GOLD", "II", 45, 10, 8,
                1445);
        RankPointResponse plat = new RankPointResponse(Instant.parse("2026-10-03T20:30:00Z"), "PLATINUM", "IV", 12,
                14, 9, 1612);
        when(service.getHistory(RiotFixtures.PUUID))
                .thenReturn(List.of(new RankHistoryResponse("RANKED_SOLO_5x5", List.of(gold, plat), plat)));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].queueType").value("RANKED_SOLO_5x5"))
                .andExpect(jsonPath("$[0].points.length()").value(2))
                .andExpect(jsonPath("$[0].points[0].recordedAt").value("2026-10-01T18:00:00Z"))
                .andExpect(jsonPath("$[0].points[0].tier").value("GOLD"))
                .andExpect(jsonPath("$[0].points[0].rank").value("II"))
                .andExpect(jsonPath("$[0].points[0].leaguePoints").value(45))
                .andExpect(jsonPath("$[0].points[0].score").value(1445))
                .andExpect(jsonPath("$[0].peak.tier").value("PLATINUM"))
                .andExpect(jsonPath("$[0].peak.score").value(1612));
    }

    @Test
    void unJoueurJamaisClasseRenvoieUneListeVide() throws Exception {
        when(service.getHistory(RiotFixtures.PUUID)).thenReturn(List.of());

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unPuuidInconnuRepond404() throws Exception {
        when(service.getHistory(RiotFixtures.PUUID))
                .thenThrow(PlayerNotFoundException.unknownPuuid(RiotFixtures.PUUID));

        mockMvc.perform(get(URL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
