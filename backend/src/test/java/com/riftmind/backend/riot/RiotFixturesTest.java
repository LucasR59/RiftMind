package com.riftmind.backend.riot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Réponses Riot de test (tâche 1.6) : cohérentes entre elles, anonymisées et utilisables avec le RiotClient. */
class RiotFixturesTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static JsonNode json(String name) {
        return JSON.readTree(RiotFixtures.read(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {RiotFixtures.ACCOUNT, RiotFixtures.ACCOUNT_NOT_FOUND, RiotFixtures.SUMMONER,
            RiotFixtures.LEAGUE_ENTRIES, RiotFixtures.LEAGUE_ENTRIES_UNRANKED, RiotFixtures.MATCH_IDS,
            RiotFixtures.MATCH})
    void chaqueFichierEstAnonymise(String name) {
        String content = RiotFixtures.read(name);

        // Toute valeur longue de type PUUID (base64url, ≥ 40 caractères, hors noms de champs) doit être factice
        Matcher longIds = Pattern.compile("\"([A-Za-z0-9_-]{40,})\"(?!\\s*:)").matcher(content);
        while (longIds.find()) {
            assertThat(longIds.group(1)).as("identifiant réel dans %s", name)
                    .matches("RiftMindTestPuuid\\d{2}_0{58}");
        }
        // Identifiants de match factices uniquement
        Matcher matchIds = Pattern.compile("EUW1_\\d+").matcher(content);
        while (matchIds.find()) {
            assertThat(matchIds.group()).startsWith("EUW1_70000000");
        }
    }

    @Test
    void leJoueurPrincipalEstLeMemeDansTousLesFichiers() {
        JsonNode account = json(RiotFixtures.ACCOUNT);
        assertThat(account.get("puuid").asString()).isEqualTo(RiotFixtures.PUUID).hasSize(78);
        assertThat(account.get("gameName").asString()).isEqualTo(RiotFixtures.GAME_NAME);
        assertThat(account.get("tagLine").asString()).isEqualTo(RiotFixtures.TAG_LINE);

        assertThat(json(RiotFixtures.SUMMONER).get("puuid").asString()).isEqualTo(RiotFixtures.PUUID);

        JsonNode leagues = json(RiotFixtures.LEAGUE_ENTRIES);
        assertThat(leagues.isArray()).isTrue();
        assertThat(leagues.get(0).get("puuid").asString()).isEqualTo(RiotFixtures.PUUID);
        assertThat(leagues.get(0).get("queueType").asString()).isEqualTo("RANKED_SOLO_5x5");

        assertThat(json(RiotFixtures.LEAGUE_ENTRIES_UNRANKED).isEmpty()).isTrue();
    }

    @Test
    void leMatchContientDixJoueursDontLeJoueurPrincipal() {
        JsonNode match = json(RiotFixtures.MATCH);
        JsonNode ids = json(RiotFixtures.MATCH_IDS);

        assertThat(ids.size()).isEqualTo(20);
        assertThat(ids.get(0).asString()).isEqualTo(RiotFixtures.MATCH_ID);
        assertThat(match.get("metadata").get("matchId").asString()).isEqualTo(RiotFixtures.MATCH_ID);
        assertThat(match.get("info").get("queueId").asInt()).isEqualTo(420);

        List<String> metadataPuuids = new ArrayList<>();
        match.get("metadata").get("participants").forEach(p -> metadataPuuids.add(p.asString()));
        List<String> participantPuuids = new ArrayList<>();
        match.get("info").get("participants").forEach(p -> participantPuuids.add(p.get("puuid").asString()));

        assertThat(participantPuuids).hasSize(10).containsExactlyElementsOf(metadataPuuids)
                .contains(RiotFixtures.PUUID);
        assertThat(participantPuuids).containsExactlyInAnyOrder(
                java.util.stream.IntStream.rangeClosed(1, 10).mapToObj(RiotFixtures::puuid).toArray(String[]::new));
        match.get("info").get("participants").forEach(p ->
                assertThat(p.get("riotIdGameName").asString()).startsWith("Joueur "));
    }

    /** Les fichiers servent de réponses simulées au {@link RiotClient}. */
    @Test
    void lesFichiersServentDeReponsesAuRiotClient() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RiotProperties properties = new RiotProperties("RGAPI-test-key-not-a-real-key",
                RiotProperties.RateLimit.defaults(), Duration.ofMinutes(2), Duration.ofSeconds(5),
                Duration.ofSeconds(10));
        RiotClient client = new RiotClient(builder, properties, new RiotRateLimiter(properties.rateLimit()));

        server.expect(requestTo("https://europe.api.riotgames.com/riot/account/v1/accounts/by-riot-id/Joueur%20Test/EUW"))
                .andRespond(withSuccess(RiotFixtures.read(RiotFixtures.ACCOUNT), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://europe.api.riotgames.com/lol/match/v5/matches/by-puuid/" + RiotFixtures.PUUID
                        + "/ids?queue=420&count=20"))
                .andRespond(withSuccess(RiotFixtures.read(RiotFixtures.MATCH_IDS), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://europe.api.riotgames.com/riot/account/v1/accounts/by-riot-id/Inconnu/ZZZZZ"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body(RiotFixtures.read(RiotFixtures.ACCOUNT_NOT_FOUND)));

        JsonNode account = client.get(Platform.EUW1.accountRoute(),
                "/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}", JsonNode.class,
                RiotFixtures.GAME_NAME, RiotFixtures.TAG_LINE);
        List<String> matchIds = client.get(Platform.EUW1.regionalRoute(),
                "/lol/match/v5/matches/by-puuid/{puuid}/ids?queue={queue}&count={count}",
                new ParameterizedTypeReference<List<String>>() { }, RiotFixtures.PUUID, 420, 20);

        assertThat(account.get("puuid").asString()).isEqualTo(RiotFixtures.PUUID);
        assertThat(matchIds).hasSize(20).first().isEqualTo(RiotFixtures.MATCH_ID);
        assertThatThrownBy(() -> client.get(RegionalRoute.EUROPE,
                "/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}", JsonNode.class, "Inconnu", "ZZZZZ"))
                .isInstanceOf(RiotNotFoundException.class)
                .hasMessageContaining("No results found for player with riot id");
        server.verify();
    }
}
