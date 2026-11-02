package com.riftmind.backend.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.riftmind.backend.exception.InvalidRequestException;
import com.riftmind.backend.exception.PlayerNotFoundException;
import com.riftmind.backend.model.Player;
import com.riftmind.backend.riot.Platform;
import com.riftmind.backend.riot.RiotFixtures;
import com.riftmind.backend.service.PlayerService;

/**
 * {@code GET /api/players/{platform}/{gameName}/{tagLine}} (tâche 2.5). Le service est simulé.
 * Les URL déjà encodées passent par {@code URI.create} : avec une chaîne, MockMvc réencoderait le {@code %}.
 */
@WebMvcTest(PlayerController.class)
class PlayerControllerTest {

    private static final Instant REFRESHED_AT = Instant.parse("2026-11-01T20:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlayerService playerService;

    private static Player mainPlayer() {
        return new Player(RiotFixtures.PUUID, "Joueur Test", "EUW", Platform.EUW1, 512, 7, REFRESHED_AT);
    }

    @Test
    void renvoieLeJoueur() throws Exception {
        when(playerService.getOrFetch(Platform.EUW1, "Joueur Test", "EUW")).thenReturn(mainPlayer());

        mockMvc.perform(get(URI.create("/api/players/euw1/Joueur%20Test/EUW")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.puuid").value(RiotFixtures.PUUID))
                .andExpect(jsonPath("$.gameName").value("Joueur Test"))
                .andExpect(jsonPath("$.tagLine").value("EUW"))
                .andExpect(jsonPath("$.platform").value("euw1"))
                .andExpect(jsonPath("$.summonerLevel").value(512))
                .andExpect(jsonPath("$.profileIconId").value(7))
                .andExpect(jsonPath("$.lastRefreshedAt").value("2026-11-01T20:00:00Z"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"euw1", "EUW1", "euw", "EUW"})
    void accepteLIdentifiantOuLAbreviationDeLaPlateforme(String platform) throws Exception {
        when(playerService.getOrFetch(any(), any(), any())).thenReturn(mainPlayer());

        mockMvc.perform(get("/api/players/{platform}/{gameName}/{tagLine}", platform, "Joueur Test", "EUW"))
                .andExpect(status().isOk());

        verify(playerService).getOrFetch(Platform.EUW1, "Joueur Test", "EUW");
    }

    @Test
    void decodeLesAccentsDuRiotId() throws Exception {
        when(playerService.getOrFetch(any(), any(), any())).thenReturn(mainPlayer());

        // « Été » encodé en UTF-8 dans l'URL
        mockMvc.perform(get(URI.create("/api/players/euw1/%C3%89t%C3%A9/FR1"))).andExpect(status().isOk());

        verify(playerService).getOrFetch(Platform.EUW1, "Été", "FR1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"xx", "ph2", "europe"})
    void unePlateformeInconnueDonne400SansAppelerLeService(String platform) throws Exception {
        mockMvc.perform(get("/api/players/{platform}/Joueur/EUW", platform))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.detail").value(containsString("Plateforme inconnue : « " + platform + " »")))
                .andExpect(jsonPath("$.detail").value(containsString("euw1")));

        verifyNoInteractions(playerService);
    }

    @Test
    void unJoueurIntrouvableDonne404AvecLeMessageDuService() throws Exception {
        when(playerService.getOrFetch(any(), any(), any()))
                .thenThrow(PlayerNotFoundException.unknownRiotId("Inconnu", "EUW", Platform.EUW1));

        mockMvc.perform(get("/api/players/euw1/Inconnu/EUW"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Aucun joueur « Inconnu#EUW » sur EUW (Europe Ouest)."));
    }

    @Test
    void unRiotIdVideDonne400() throws Exception {
        when(playerService.getOrFetch(any(), any(), any()))
                .thenThrow(new InvalidRequestException("Le nom du Riot ID ne doit pas être vide."));

        mockMvc.perform(get(URI.create("/api/players/euw1/%20/EUW")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.detail").value("Le nom du Riot ID ne doit pas être vide."));
    }

    @Test
    void unRiotIdIncompletNeCorrespondAAucuneRoute() throws Exception {
        mockMvc.perform(get(URI.create("/api/players/euw1/Joueur%20Test"))).andExpect(status().isNotFound());

        verifyNoInteractions(playerService);
    }
}
