package com.riftmind.backend.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.riftmind.backend.assets.AssetPath.Kind;

/** Liste blanche des chemins de la copie locale : ce qui est servi, et surtout tout ce qui est refusé. */
class AssetPathTest {

    private static final String LOCALE = "fr_FR";

    @ParameterizedTest
    @CsvSource({
            "ddragon/versions.json, VERSIONS, ",
            "ddragon/cdn/16.21.1/data/fr_FR/champion.json, DATA, 16.21.1",
            "ddragon/cdn/16.21.1/data/fr_FR/item.json, DATA, 16.21.1",
            "ddragon/cdn/16.21.1/data/fr_FR/summoner.json, DATA, 16.21.1",
            "ddragon/cdn/16.21.1/data/fr_FR/runesReforged.json, DATA, 16.21.1",
            "ddragon/cdn/16.21.1/img/champion/MonkeyKing.png, IMAGE, 16.21.1",
            "ddragon/cdn/16.9.1/img/item/3087.png, IMAGE, 16.9.1",
            "ddragon/cdn/16.21.1/img/spell/SummonerFlash.png, IMAGE, 16.21.1",
            "ddragon/cdn/16.21.1/img/profileicon/7.png, PROFILE_ICON, 16.21.1",
            "ddragon/cdn/img/perk-images/Styles/7200_Domination.png, STATIC_IMAGE, ",
            "ddragon/cdn/img/perk-images/Styles/Sorcery/PhaseRush/StormraidersSurgeRuneIcon2.png, STATIC_IMAGE, ",
            "ranks/challenger.png, RANK, ",
            "ranks/emerald.png, RANK, ",
            "ranks/unranked.png, RANK, ",
    })
    void reconnaitLesCheminsServis(String path, Kind kind, String version) {
        assertThat(AssetPath.parse(path, LOCALE)).contains(new AssetPath(path, kind, version));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "/ddragon/versions.json",
            // Remontée dans l'arborescence, sous toutes ses formes
            "../application.properties",
            "ddragon/cdn/16.21.1/img/champion/../../../../etc/passwd",
            "ddragon/cdn/../../secret.png",
            "ddragon/cdn/16.21.1/img/champion/..png",
            "ddragon/cdn/16.21.1/img/champion/%2e%2e%2fsecret.png",
            "ddragon/cdn/16.21.1/img/champion/..%2Fsecret.png",
            "ddragon/cdn/16.21.1/img/champion/..\\secret.png",
            "ddragon/cdn/img/perk-images/../../../secret.png",
            "ddragon/cdn/img/perk-images/Styles/.hidden.png",
            "ddragon\\cdn\\16.21.1\\img\\item\\1001.png",
            "C:/Windows/win.ini",
            // Fichiers internes de la copie locale
            "ddragon/cdn/16.21.1/.complete",
            "ddragon/cdn/16.21.1/img/champion/.download-123.tmp",
            // Versions invalides
            "ddragon/cdn/latest/img/champion/Kalista.png",
            "ddragon/cdn/16.21/img/champion/Kalista.png",
            "ddragon/cdn/lolpatch_7.20/img/champion/Kalista.png",
            // Autre langue, autre fichier, autre dossier
            "ddragon/cdn/16.21.1/data/en_US/champion.json",
            "ddragon/cdn/16.21.1/data/fr_FR/profileicon.json",
            "ddragon/cdn/16.21.1/data/fr_FR/champion/Kalista.json",
            "ddragon/cdn/16.21.1/img/passive/Kalista_Passive.png",
            "ddragon/cdn/16.21.1/img/champion/splash/Kalista_0.jpg",
            "ddragon/api/versions.json",
            "ddragon/cdn/dragontail-16.21.1.tgz",
            // Noms et extensions invalides
            "ddragon/cdn/16.21.1/img/champion/Kalista.PNG",
            "ddragon/cdn/16.21.1/img/champion/Kalista.png?x=1",
            "ddragon/cdn/16.21.1/img/champion/Kal ista.png",
            "ddragon/cdn/16.21.1/img/item/abc.png",
            "ddragon/cdn/16.21.1/img/profileicon/12345678.png",
            "ddragon/cdn/16.21.1/img/champion/Kalista.png/",
            // Emblèmes de rang : seulement les paliers connus, en PNG
            "ranks/wood.png",
            "ranks/Gold.png",
            "ranks/gold.svg",
            "ranks/gold/gold.png",
            "ranks/../ddragon/versions.json",
            "ranks/../../../../etc/passwd",
            // Anciens chemins de CommunityDragon : plus servis
            "cdragon/ranked-emblem/emblem-gold.png",
            "cdragon/ranked-mini-crests/gold.svg",
    })
    void refuseToutLeReste(String path) {
        assertThat(AssetPath.parse(path, LOCALE)).isEmpty();
    }

    @Test
    void refuseUnCheminNulOuTropLong() {
        assertThat(AssetPath.parse(null, LOCALE)).isEmpty();
        assertThat(AssetPath.parse("ddragon/cdn/img/perk-images/" + "a/".repeat(100) + "a.png", LOCALE)).isEmpty();
    }

    @Test
    void unCheminAccepteNeSortJamaisDuDossier() {
        Path root = Path.of("copie").toAbsolutePath().normalize();
        List<String> accepted = new ArrayList<>(AssetPath.rankImages());
        accepted.add("ddragon/cdn/img/perk-images/a/b/c/d/e/f.png");
        accepted.add("ddragon/cdn/9999.9999.9999/img/profileicon/9999999.png");
        for (String path : accepted) {
            assertThat(AssetPath.parse(path, LOCALE)).isPresent();
            assertThat(root.resolve(path).normalize()).startsWithRaw(root);
        }
    }

    @Test
    void lesOnzeEmblemesDeRangSontAutorises() {
        assertThat(AssetPath.rankImages()).hasSize(11)
                .contains("ranks/iron.png", "ranks/emerald.png", "ranks/challenger.png", "ranks/unranked.png")
                .allSatisfy(path -> assertThat(AssetPath.parse(path, LOCALE))
                        .hasValueSatisfying(asset -> assertThat(asset.kind()).isEqualTo(Kind.RANK)));
    }

    @Test
    void unEmblemeDeRangNEstJamaisTelecharge() {
        AssetPath gold = AssetPath.parse("ranks/gold.png", LOCALE).orElseThrow();

        assertThatThrownBy(() -> gold.upstreamUrl(AssetProperties.of(Path.of("x"), null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void trieLesVersionsDeLaPlusRecenteALaPlusAncienne() {
        List<String> versions = new ArrayList<>(List.of("16.9.1", "15.24.1", "16.21.1", "16.21.2", "17.1.1"));

        versions.sort(AssetPath.NEWEST_FIRST);

        assertThat(versions).containsExactly("17.1.1", "16.21.2", "16.21.1", "16.9.1", "15.24.1");
    }

    @Test
    void construitLAdresseChezDataDragon() {
        AssetProperties properties = AssetProperties.of(Path.of("x"), " ");

        assertThat(url("ddragon/versions.json", properties))
                .isEqualTo("https://ddragon.leagueoflegends.com/api/versions.json");
        assertThat(url("ddragon/cdn/16.21.1/img/champion/Kalista.png", properties))
                .isEqualTo("https://ddragon.leagueoflegends.com/cdn/16.21.1/img/champion/Kalista.png");
        assertThat(url("ddragon/cdn/img/perk-images/Styles/7200_Domination.png", properties))
                .isEqualTo("https://ddragon.leagueoflegends.com/cdn/img/perk-images/Styles/7200_Domination.png");
        assertThat(properties.simulated()).isFalse();
    }

    @Test
    void uneFausseAdresseGardeSonCheminSansBarreFinale() {
        AssetProperties properties = AssetProperties.of(Path.of("x"), "http://wiremock:8080/ddragon/");

        assertThat(properties.simulated()).isTrue();
        assertThat(url("ddragon/cdn/16.21.1/data/fr_FR/item.json", properties))
                .isEqualTo("http://wiremock:8080/ddragon/cdn/16.21.1/data/fr_FR/item.json");
    }

    @Test
    void lesFichiersVersionnesSontEnCacheUnAnLesAutresUnJour() {
        assertThat(Kind.IMAGE.cacheControl().getHeaderValue()).isEqualTo("max-age=31536000, public, immutable");
        assertThat(Kind.DATA.cacheControl().getHeaderValue()).isEqualTo("max-age=31536000, public, immutable");
        assertThat(Kind.RANK.cacheControl().getHeaderValue()).isEqualTo("max-age=86400, public");
        assertThat(Kind.VERSIONS.cacheControl().getHeaderValue()).isEqualTo("max-age=300, public");
    }

    private static String url(String path, AssetProperties properties) {
        return AssetPath.parse(path, LOCALE).orElseThrow().upstreamUrl(properties);
    }
}
