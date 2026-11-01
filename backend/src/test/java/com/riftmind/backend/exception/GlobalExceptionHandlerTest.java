package com.riftmind.backend.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.riftmind.backend.riot.RiotApiException;
import com.riftmind.backend.riot.RiotApiKeyException;
import com.riftmind.backend.riot.RiotNotFoundException;
import com.riftmind.backend.riot.RiotRateLimitException;
import com.riftmind.backend.riot.RiotUnavailableException;

/** Réponses HTTP renvoyées au front pour chaque type d'erreur (tâche 1.4). */
@WebMvcTest(GlobalExceptionHandlerTest.ThrowingController.class)
@Import(GlobalExceptionHandlerTest.ThrowingController.class)
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private static final String HOST = "euw1.api.riotgames.com";

    /** Contrôleur de test qui lève chaque exception (exclu du scan de l'application : classe imbriquée de test). */
    @RestController
    static class ThrowingController {

        @GetMapping("/test/not-found")
        void notFound() {
            throw new RiotNotFoundException(HOST, "Introuvable sur " + HOST + " : Data not found");
        }

        @GetMapping("/test/rate-limit")
        void rateLimit() {
            throw new RiotRateLimitException(HOST, Duration.ofMillis(6_200), "application");
        }

        @GetMapping("/test/api-key")
        void apiKey() {
            throw new RiotApiKeyException(403, HOST);
        }

        @GetMapping("/test/unavailable")
        void unavailable() {
            throw new RiotUnavailableException(503, HOST, "Erreur 503 de l'API Riot");
        }

        @GetMapping("/test/riot-error")
        void riotError() {
            throw new RiotApiException(400, HOST, "Réponse inattendue 400");
        }

        @GetMapping("/test/resource-not-found")
        void resourceNotFound() {
            throw new ResourceNotFoundException("Match EUW1_1 introuvable");
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void riotNotFoundRenvoie404() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Ressource introuvable"));
    }

    @Test
    void limiteRiotRenvoie503AvecRetryAfterArrondiAuDessus() throws Exception {
        mockMvc.perform(get("/test/rate-limit"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "7"))
                .andExpect(jsonPath("$.code").value("RIOT_RATE_LIMITED"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(7))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("7 s")));
    }

    @Test
    void cleRefuseeRenvoieUnMessageNeutreEtLogueLaCause(CapturedOutput output) throws Exception {
        String body = mockMvc.perform(get("/test/api-key"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("RIOT_UNAVAILABLE"))
                .andReturn().getResponse().getContentAsString();

        // Le public ne doit pas apprendre que notre clé est expirée…
        assertThat(body).doesNotContain("RIOT_API_KEY").doesNotContainIgnoringCase("clé");
        // …mais le développeur doit le voir clairement dans les logs
        assertThat(output).contains("ERROR").contains("Clé API Riot refusée (403)").contains("RIOT_API_KEY");
    }

    @Test
    void riotIndisponibleRenvoie503() throws Exception {
        mockMvc.perform(get("/test/unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("RIOT_UNAVAILABLE"))
                .andExpect(jsonPath("$.title").value("Serveurs Riot indisponibles"));
    }

    @Test
    void erreurRiotInattendueRenvoie502() throws Exception {
        mockMvc.perform(get("/test/riot-error"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("RIOT_ERROR"));
    }

    @Test
    void ressourceIntrouvableRenvoie404AvecSonMessage() throws Exception {
        mockMvc.perform(get("/test/resource-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Match EUW1_1 introuvable"));
    }
}
