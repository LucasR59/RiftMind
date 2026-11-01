package com.riftmind.backend.riot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RiotClientTest {

    private static final String FAKE_KEY = "RGAPI-test-key-not-a-real-key";

    private MockRestServiceServer server;
    private RiotClient client;

    record AccountDto(String puuid, String gameName, String tagLine) {
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RiotProperties properties = new RiotProperties(FAKE_KEY, RiotProperties.RateLimit.defaults(),
                Duration.ofMinutes(2), Duration.ofSeconds(5), Duration.ofSeconds(10));
        client = new RiotClient(builder, properties, new RiotRateLimiter(properties.rateLimit()));
    }

    @Test
    void appelleLHoteDeLaPlateformeAvecLaCle() {
        server.expect(requestTo("https://euw1.api.riotgames.com/lol/status/v4/platform-data"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Riot-Token", FAKE_KEY))
                .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess("{\"id\":\"EUW1\",\"name\":\"EU West\"}", MediaType.APPLICATION_JSON));

        record Status(String id, String name) {
        }
        Status status = client.get(Platform.EUW1, "/lol/status/v4/platform-data", Status.class);

        assertThat(status).isEqualTo(new Status("EUW1", "EU West"));
        server.verify();
    }

    @Test
    void appelleLHoteDeLaRegionEtEncodeLeRiotId() {
        server.expect(requestTo(
                        "https://europe.api.riotgames.com/riot/account/v1/accounts/by-riot-id/Hide%20on%20bush/KR1"))
                .andExpect(header("X-Riot-Token", FAKE_KEY))
                .andRespond(withSuccess("{\"puuid\":\"abc\",\"gameName\":\"Hide on bush\",\"tagLine\":\"KR1\"}",
                        MediaType.APPLICATION_JSON));

        AccountDto account = client.get(RegionalRoute.EUROPE,
                "/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}", AccountDto.class, "Hide on bush", "KR1");

        assertThat(account.puuid()).isEqualTo("abc");
        server.verify();
    }

    @Test
    void encodeLesCaracteresQuiPourraientDetournerLUrl() {
        // « / », « # », « ? » et les accents ne doivent pas changer le chemin appelé
        server.expect(requestTo("https://europe.api.riotgames.com/riot/account/v1/accounts/by-riot-id/"
                        + "a%2Fb%23c%3Fd/%C3%89t%C3%A9"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.get(RegionalRoute.EUROPE, "/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}",
                AccountDto.class, "a/b#c?d", "Été");

        server.verify();
    }

    @Test
    void gereLesParametresDeRequeteEtLesListes() {
        server.expect(requestTo(
                        "https://europe.api.riotgames.com/lol/match/v5/matches/by-puuid/abc/ids?start=0&count=20"))
                .andRespond(withSuccess("[\"EUW1_1\",\"EUW1_2\"]", MediaType.APPLICATION_JSON));

        List<String> ids = client.get(Platform.EUW1.regionalRoute(),
                "/lol/match/v5/matches/by-puuid/{puuid}/ids?start={start}&count={count}",
                new ParameterizedTypeReference<List<String>>() {
                }, "abc", 0, 20);

        assertThat(ids).containsExactly("EUW1_1", "EUW1_2");
        server.verify();
    }

    @Test
    void routeLaRechercheDeCompteSeaVersAsia() {
        server.expect(requestTo("https://asia.api.riotgames.com/riot/account/v1/accounts/by-puuid/abc"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.get(Platform.SG2.accountRoute(), "/riot/account/v1/accounts/by-puuid/{puuid}", AccountDto.class, "abc");

        server.verify();
    }

    @Test
    void refuseUnCheminSansSlashInitial() {
        assertThatThrownBy(() -> client.get(Platform.EUW1, "lol/status/v4/platform-data", String.class))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.get(RegionalRoute.EUROPE, ".evil.com/x", String.class))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
