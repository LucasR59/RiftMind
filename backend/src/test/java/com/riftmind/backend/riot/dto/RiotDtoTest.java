package com.riftmind.backend.riot.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
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
 * Lecture des réponses Riot en DTO (tâches 2.1, 3.2 et 4.1), via le vrai chemin : {@link RiotClient} et ses convertisseurs JSON.
 * {@code RestClient.builder()} utilise la configuration Jackson par défaut, et non celle de l'application.
 */
class RiotDtoTest {

    private static final String ACCOUNT_URL =
            "https://europe.api.riotgames.com/riot/account/v1/accounts/by-riot-id/Joueur%20Test/EUW";
    private static final String SUMMONER_URL =
            "https://euw1.api.riotgames.com/lol/summoner/v4/summoners/by-puuid/" + RiotFixtures.PUUID;
    private static final String LEAGUE_URL =
            "https://euw1.api.riotgames.com/lol/league/v4/entries/by-puuid/" + RiotFixtures.PUUID;
    private static final String MATCH_IDS_URL = "https://europe.api.riotgames.com/lol/match/v5/matches/by-puuid/"
            + RiotFixtures.PUUID + "/ids?start=0&count=20";
    private static final String MATCH_URL =
            "https://europe.api.riotgames.com/lol/match/v5/matches/" + RiotFixtures.MATCH_ID;

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

    private List<LeagueEntryDto> fetchLeagueEntries(String fixture) {
        server.expect(requestTo(LEAGUE_URL))
                .andRespond(withSuccess(RiotFixtures.read(fixture), MediaType.APPLICATION_JSON));
        return client.get(Platform.EUW1, "/lol/league/v4/entries/by-puuid/{puuid}",
                new ParameterizedTypeReference<List<LeagueEntryDto>>() {
                }, RiotFixtures.PUUID);
    }

    @Test
    void litLesRangsDUnJoueurClasse() {
        // league-entries.json contient aussi leagueId, veteran, inactive, freshBlood, absents du DTO
        List<LeagueEntryDto> entries = fetchLeagueEntries(RiotFixtures.LEAGUE_ENTRIES);

        assertThat(entries).containsExactly(
                new LeagueEntryDto("RANKED_SOLO_5x5", "CHALLENGER", "I", 1234, 250, 200, false));
        server.verify();
    }

    @Test
    void unJoueurNonClasseDonneUneListeVide() {
        assertThat(fetchLeagueEntries(RiotFixtures.LEAGUE_ENTRIES_UNRANKED)).isEmpty();
    }

    @Test
    void litLesIdentifiantsDesDerniersMatchs() {
        server.expect(requestTo(MATCH_IDS_URL))
                .andRespond(withSuccess(RiotFixtures.read(RiotFixtures.MATCH_IDS), MediaType.APPLICATION_JSON));

        List<String> ids = client.get(Platform.EUW1.regionalRoute(),
                "/lol/match/v5/matches/by-puuid/{puuid}/ids?start={start}&count={count}",
                new ParameterizedTypeReference<List<String>>() {
                }, RiotFixtures.PUUID, 0, 20);

        assertThat(ids).hasSize(20).first().isEqualTo(RiotFixtures.MATCH_ID);
        server.verify();
    }

    private MatchDto fetchMatch() {
        server.expect(requestTo(MATCH_URL))
                .andRespond(withSuccess(RiotFixtures.read(RiotFixtures.MATCH), MediaType.APPLICATION_JSON));
        return client.get(Platform.EUW1.regionalRoute(), "/lol/match/v5/matches/{matchId}", MatchDto.class,
                RiotFixtures.MATCH_ID);
    }

    @Test
    void litUnMatch() {
        MatchDto match = fetchMatch();

        assertThat(match.metadata().matchId()).isEqualTo(RiotFixtures.MATCH_ID);
        assertThat(match.metadata().participants()).hasSize(10).contains(RiotFixtures.PUUID);
        InfoDto info = match.info();
        assertThat(info.queueId()).isEqualTo(420);
        assertThat(info.platformId()).isEqualTo("EUW1");
        assertThat(info.gameMode()).isEqualTo("CLASSIC");
        assertThat(info.mapId()).isEqualTo(11);
        assertThat(info.gameVersion()).isEqualTo("16.21.834.2754");
        assertThat(info.gameStart()).isEqualTo(Instant.ofEpochMilli(1790951450908L));
        assertThat(info.gameEndTimestamp()).isEqualTo(1790952914156L);
        assertThat(info.durationSeconds()).isEqualTo(1463);
        assertThat(info.participants()).hasSize(10);
    }

    @Test
    void litLesStatistiquesDUnJoueur() {
        ParticipantDto me = fetchMatch().info().participants().stream()
                .filter(p -> p.puuid().equals(RiotFixtures.PUUID)).findFirst().orElseThrow();

        assertThat(me.participantId()).isEqualTo(4);
        assertThat(me.riotIdGameName()).isEqualTo("Joueur Test");
        assertThat(me.riotIdTagline()).isEqualTo("EUW");
        assertThat(me.teamId()).isEqualTo(100);
        assertThat(me.championId()).isEqualTo(429);
        assertThat(me.championName()).isEqualTo("Kalista");
        assertThat(me.champLevel()).isEqualTo(15);
        assertThat(me.teamPosition()).isEqualTo("BOTTOM");
        assertThat(me.win()).isTrue();
        assertThat(me.kills()).isEqualTo(6);
        assertThat(me.deaths()).isEqualTo(4);
        assertThat(me.assists()).isEqualTo(3);
        assertThat(me.cs()).as("227 sbires + 23 monstres").isEqualTo(250);
        assertThat(me.goldEarned()).isEqualTo(13852);
        assertThat(me.totalDamageDealtToChampions()).isEqualTo(19708);
        assertThat(me.visionScore()).isEqualTo(19);
        assertThat(me.items()).containsExactly(1086, 3087, 3124, 1082, 3302, 1011, 3363);
        assertThat(me.summonerSpells()).containsExactly(4, 21);
        assertThat(me.keystone()).isEqualTo(8008);
        assertThat(me.secondaryStyle()).isEqualTo(8400);
        assertThat(me.gameEndedInEarlySurrender()).isFalse();
    }

    @Test
    void lesRunesAbsentesDonnentZero() {
        ParticipantDto noPerks = new ParticipantDto(1, "p", "n", "t", 100, 1, "Annie", 1, "", true, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 7, null, false);
        ParticipantDto emptyStyle = new ParticipantDto(1, "p", "n", "t", 100, 1, "Annie", 1, "", true, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 7,
                new ParticipantDto.PerksDto(List.of(new ParticipantDto.PerkStyleDto("primaryStyle", 8100, List.of()))),
                false);

        assertThat(noPerks.keystone()).isZero();
        assertThat(noPerks.secondaryStyle()).isZero();
        assertThat(emptyStyle.keystone()).isZero();
        assertThat(emptyStyle.secondaryStyle()).isZero();
    }

    @Test
    void detecteUnRemake() {
        JsonMapper mapper = JsonMapper.builder().build();

        assertThat(fetchMatch().info().isRemake()).isFalse();
        InfoDto remake = mapper.readValue(RiotFixtures.remakeMatch(), MatchDto.class).info();
        assertThat(remake.isRemake()).isTrue();
        assertThat(remake.durationSeconds()).isEqualTo(210);
    }

    @Test
    void unePartieSansParticipantNEstPasUnRemake() {
        assertThat(new InfoDto(0, 0, 1L, 0, "16.21", 420, "EUW1", "CLASSIC", 11, null).isRemake()).isFalse();
    }

    @Test
    void avantLePatch1120LaDureeEstEnMillisecondes() {
        // Sans gameEndTimestamp, Riot donnait gameDuration en millisecondes
        InfoDto old = new InfoDto(0, 0, null, 1_463_000, "11.19.1", 420, "EUW1", "CLASSIC", 11, List.of());

        assertThat(old.durationSeconds()).isEqualTo(1463);
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
        assertThat(strict.readValue(RiotFixtures.read(RiotFixtures.LEAGUE_ENTRIES), LeagueEntryDto[].class))
                .extracting(LeagueEntryDto::tier).containsExactly("CHALLENGER");
        // match.json contient ~150 champs par joueur, et des objets entiers (challenges, missions…) non lus
        assertThat(strict.readValue(RiotFixtures.read(RiotFixtures.MATCH), MatchDto.class).info().participants())
                .hasSize(10);
    }
}
