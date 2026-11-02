package com.riftmind.backend.riot;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Vraies réponses de l'API Riot, anonymisées, pour les tests ({@code src/test/resources/riot/}).
 * Elles sont régénérées par {@code node scripts/riot-fixtures.mjs} (voir le README du dossier).
 *
 * <p>Exemple avec {@code MockRestServiceServer} :
 * <pre>
 * server.expect(requestTo(...)).andRespond(withSuccess(RiotFixtures.read(RiotFixtures.ACCOUNT), MediaType.APPLICATION_JSON));
 * </pre>
 */
public final class RiotFixtures {

    /** Account-V1 {@code /riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}} (ou by-puuid). */
    public static final String ACCOUNT = "account.json";
    /** Account-V1 : corps de la réponse 404 pour un Riot ID inexistant. */
    public static final String ACCOUNT_NOT_FOUND = "account-not-found.json";
    /** Summoner-V4 {@code /lol/summoner/v4/summoners/by-puuid/{puuid}}. */
    public static final String SUMMONER = "summoner.json";
    /** League-V4 {@code /lol/league/v4/entries/by-puuid/{puuid}} d'un joueur classé en SoloQ. */
    public static final String LEAGUE_ENTRIES = "league-entries.json";
    /** League-V4 pour un joueur non classé : liste vide. */
    public static final String LEAGUE_ENTRIES_UNRANKED = "league-entries-unranked.json";
    /** Match-V5 {@code /lol/match/v5/matches/by-puuid/{puuid}/ids?queue=420&count=20} : 20 identifiants. */
    public static final String MATCH_IDS = "match-ids.json";
    /** Match-V5 {@code /lol/match/v5/matches/{matchId}} : partie SoloQ classée de 10 joueurs. */
    public static final String MATCH = "match.json";

    /** Joueur principal, présent dans tous les fichiers. */
    public static final String PUUID = puuid(1);
    public static final String GAME_NAME = "Joueur Test";
    public static final String TAG_LINE = "EUW";
    /** Identifiant du match de {@link #MATCH}, premier de {@link #MATCH_IDS}. */
    public static final String MATCH_ID = "EUW1_7000000001";

    private RiotFixtures() {
    }

    /** Contenu d'un fichier de {@code src/test/resources/riot/}. */
    public static String read(String name) {
        try (InputStream in = RiotFixtures.class.getResourceAsStream("/riot/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("Fichier de test Riot introuvable : riot/" + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * {@link #MATCH} transformé en remake (tâche 4.5) : tous les joueurs ont {@code gameEndedInEarlySurrender} et la
     * partie dure 3 min 30. Aucun vrai remake n'a été trouvé en récupérant les fichiers (environ 1 % des parties).
     */
    public static String remakeMatch() {
        String match = read(MATCH);
        String remake = match.replace("\"gameEndedInEarlySurrender\": false", "\"gameEndedInEarlySurrender\": true")
                .replace("\"gameDuration\": 1463", "\"gameDuration\": 210");
        if (remake.equals(match) || remake.contains("\"gameEndedInEarlySurrender\": false")) {
            throw new IllegalStateException("match.json a changé de format : adapter RiotFixtures.remakeMatch()");
        }
        return remake;
    }

    /** PUUID factice du n-ième joueur (1 = joueur principal, 2 à 10 = les autres joueurs de {@link #MATCH}). */
    public static String puuid(int n) {
        return ("RiftMindTestPuuid%02d_".formatted(n)).concat("0".repeat(58));
    }
}
