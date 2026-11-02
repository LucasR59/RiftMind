package com.riftmind.backend;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.riftmind.backend.model.Match;
import com.riftmind.backend.model.MatchParticipant;
import com.riftmind.backend.repository.MatchParticipantRepository;
import com.riftmind.backend.repository.MatchRepository;
import com.riftmind.backend.riot.RiotFixtures;
import com.riftmind.backend.riot.dto.ParticipantDto;

/**
 * Construit des parties de test aux valeurs choisies, pour vérifier l'historique et les statistiques sur des données
 * connues. Le joueur principal ({@link RiotFixtures#PUUID}) est le participant 1, dans l'équipe bleue ; les 9 autres
 * sont des joueurs factices ({@code RiotFixtures.puuid(2)} à {@code (10)}).
 *
 * <pre>
 * TestMatches.match("EUW1_1").queue(420).start(t).duration(1800)
 *         .me(429, "Kalista", "BOTTOM", true, 6, 4, 3, 250, 19708)
 *         .save(matchRepository, participantRepository);
 * </pre>
 */
public final class TestMatches {

    private final String matchId;
    private int queueId = 420;
    private Instant start = Instant.parse("2026-11-01T20:00:00Z");
    private int durationSeconds = 1800;
    private boolean remake;
    private ParticipantDto me = participant(1, RiotFixtures.PUUID, "Joueur Test", "EUW", 100, 429, "Kalista",
            "BOTTOM", true, 6, 4, 3, 250, 19708);

    private TestMatches(String matchId) {
        this.matchId = matchId;
    }

    public static TestMatches match(String matchId) {
        return new TestMatches(matchId);
    }

    public TestMatches queue(int queueId) {
        this.queueId = queueId;
        return this;
    }

    public TestMatches start(Instant start) {
        this.start = start;
        return this;
    }

    public TestMatches duration(int durationSeconds) {
        this.durationSeconds = durationSeconds;
        return this;
    }

    public TestMatches remake() {
        this.remake = true;
        return this;
    }

    /** Statistiques du joueur principal dans cette partie. */
    public TestMatches me(int championId, String championName, String position, boolean win, int kills, int deaths,
            int assists, int cs, int damage) {
        this.me = participant(1, RiotFixtures.PUUID, "Joueur Test", "EUW", 100, championId, championName, position,
                win, kills, deaths, assists, cs, damage);
        return this;
    }

    /** Enregistre la partie et ses 10 joueurs. */
    public Match save(MatchRepository matchRepository, MatchParticipantRepository participantRepository) {
        Match match = matchRepository.save(new Match(matchId, "EUW1", queueId, "16.21.834.2754", start,
                durationSeconds, remake, null, start));
        List<MatchParticipant> players = new ArrayList<>();
        players.add(MatchParticipant.of(match, me));
        for (int n = 2; n <= 10; n++) {
            int team = n <= 5 ? 100 : 200;
            boolean win = team == 100 ? me.win() : !me.win();
            players.add(MatchParticipant.of(match, participant(n, RiotFixtures.puuid(n), "Joueur " + n, "TEST", team,
                    n, "Champion" + n, "", win, 1, 1, 1, 100, 10000)));
        }
        participantRepository.saveAll(players);
        return match;
    }

    /** Participant aux valeurs choisies ; objets, sorts et runes factices. */
    public static ParticipantDto participant(int participantId, String puuid, String gameName, String tagLine,
            int teamId, int championId, String championName, String position, boolean win, int kills, int deaths,
            int assists, int cs, int damage) {
        return new ParticipantDto(participantId, puuid, gameName, tagLine, teamId, championId, championName, 15,
                position, win, kills, deaths, assists, cs, 0, 12000, damage, 20,
                1001, 3006, 0, 0, 0, 0, 3340, 4, 14,
                new ParticipantDto.PerksDto(List.of(
                        new ParticipantDto.PerkStyleDto("primaryStyle", 8100,
                                List.of(new ParticipantDto.PerkSelectionDto(8112))),
                        new ParticipantDto.PerkStyleDto("subStyle", 8000, List.of()))),
                false);
    }
}
