package com.riftmind.backend.riot.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.riftmind.backend.riot.Platform;
import com.riftmind.backend.riot.RiotClient;
import com.riftmind.backend.riot.RiotFixtures;
import com.riftmind.backend.riot.RiotProperties;
import com.riftmind.backend.riot.RiotRateLimiter;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lecture des réponses Riot en DTO (tâche 2.1), via le vrai chemin : {@link RiotClient} et ses convertisseurs JSON.
 * {@code RestClient.builder()} utilise la configuration Jackson par défaut, et non celle de l'application.
 */
class RiotDtoTest {

    private static final String ACCOUNT_URL =
            "https://europe.api.riotgames.com/riot/account/v1/accounts/by-riot-id/Joueur%20Test/EUW";
    private static final String SUMMONER_URL =
            "https://euw1.api.riotgames.com/lol/summoner/v4/summoners/by-puuid/" + RiotFixtures.PUUID;

    private MockRestServiceServer server;
    private RiotClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RiotProperties properties = new RiotProperties("RGAPI-test-key-not-a-real-key",
                RiotProperties.RateLimit.defaults(), Duration.ofMinutes(2), Duration.ofSeconds(5),
                Duration.ofSeconds(10));
        client = new RiotClient(builder, properties, new RiotRateLimiter(properties.rateLimit()));
    }

    private AccountDto fetchAccount(String json) {
        server.expect(requestTo(ACCOUNT_URL)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        return client.get(Platform.EUW1.accountRoute(), "/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}",
                AccountDto.class, RiotFixtures.GAME_NAME, RiotFixtures.TAG_LINE);
    }

    @Test
    void litUnCompteRiot() {
        AccountDto account = fetchAccount(RiotFixtures.read(RiotFixtures.ACCOUNT));

        assertThat(account).isEqualTo(new AccountDto(RiotFixtures.PUUID, "Joueur Test", "EUW"));
    }

    @Test
    void unCompteSansRiotIdDonneDesChampsNuls() {
        // Riot omet gameName et tagLine si le compte n'en a pas
        AccountDto account = fetchAccount("{\"puuid\": \"" + RiotFixtures.PUUID + "\"}");

        assertThat(account.puuid()).isEqualTo(RiotFixtures.PUUID);
        assertThat(account.gameName()).isNull();
        assertThat(account.tagLine()).isNull();
    }

    @Test
    void ignoreLesChampsAjoutesParRiot() {
        AccountDto account = fetchAccount("""
                {"puuid": "%s", "gameName": "Joueur Test", "tagLine": "EUW", "nouveauChamp": {"a": 1}}
                """.formatted(RiotFixtures.PUUID));

        assertThat(account.gameName()).isEqualTo("Joueur Test");
    }

    @Test
    void litUnInvocateurEnIgnorantLesChampsInutiles() {
        // summoner.json contient aussi revisionDate, absent du DTO
        server.expect(requestTo(SUMMONER_URL))
                .andRespond(withSuccess(RiotFixtures.read(RiotFixtures.SUMMONER), MediaType.APPLICATION_JSON));

        SummonerDto summoner = client.get(Platform.EUW1, "/lol/summoner/v4/summoners/by-puuid/{puuid}",
                SummonerDto.class, RiotFixtures.PUUID);

        assertThat(summoner).isEqualTo(new SummonerDto(RiotFixtures.PUUID, 7, 512));
        server.verify();
    }

    /**
     * Jackson 3 ignore déjà les champs inconnus par défaut. {@code @JsonIgnoreProperties} garantit que les DTO
     * restent lisibles même si la configuration devient stricte
     * (ex. {@code spring.jackson.deserialization.fail-on-unknown-properties=true}).
     */
    @Test
    void lesDtoRestentLisiblesAvecUneConfigurationJacksonStricte() {
        JsonMapper strict = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        assertThat(strict.readValue(RiotFixtures.read(RiotFixtures.SUMMONER), SummonerDto.class).summonerLevel())
                .isEqualTo(512);
        assertThat(strict.readValue("{\"puuid\": \"p\", \"nouveauChamp\": 1}", AccountDto.class).puuid())
                .isEqualTo("p");
    }
}
