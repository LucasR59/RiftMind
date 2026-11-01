package com.riftmind.backend.riot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PlatformTest {

    /** Correspondances vérifiées en direct le 2026-11-01 (docs/riot-api.md §3). */
    @ParameterizedTest(name = "{0} -> {1} (account : {2})")
    @CsvSource({
            "br1,  AMERICAS, AMERICAS",
            "la1,  AMERICAS, AMERICAS",
            "la2,  AMERICAS, AMERICAS",
            "na1,  AMERICAS, AMERICAS",
            "eun1, EUROPE,   EUROPE",
            "euw1, EUROPE,   EUROPE",
            "me1,  EUROPE,   EUROPE",
            "ru,   EUROPE,   EUROPE",
            "tr1,  EUROPE,   EUROPE",
            "jp1,  ASIA,     ASIA",
            "kr,   ASIA,     ASIA",
            "oc1,  SEA,      ASIA",
            "sg2,  SEA,      ASIA",
            "tw2,  SEA,      ASIA",
            "vn2,  SEA,      ASIA",
    })
    void chaquePlateformeEstRattacheeASaRegion(String id, RegionalRoute matchRoute, RegionalRoute accountRoute) {
        Platform platform = Platform.fromValue(id);

        assertThat(platform.id()).isEqualTo(id);
        assertThat(platform.regionalRoute()).isEqualTo(matchRoute);
        assertThat(platform.accountRoute()).isEqualTo(accountRoute);
    }

    @Test
    void gereLesQuinzePlateformesActivesUniquement() {
        assertThat(Platform.values()).hasSize(15);
        // Serveurs fusionnés dans sg2 : leurs hôtes ne répondent plus
        assertThat(Platform.find("ph2")).isEmpty();
        assertThat(Platform.find("th2")).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Platform.class)
    void accountV1NePassePasParSea(Platform platform) {
        assertThat(platform.accountRoute()).isNotEqualTo(RegionalRoute.SEA);
    }

    @Test
    void identifiantsEtAbreviationsSontUniques() {
        assertThat(Arrays.stream(Platform.values()).map(Platform::id)).doesNotHaveDuplicates();
        assertThat(Arrays.stream(Platform.values()).map(Platform::shortName)).doesNotHaveDuplicates();
    }

    @Test
    void construitLesHotesEtLePrefixeDesMatchs() {
        assertThat(Platform.EUW1.host()).isEqualTo("euw1.api.riotgames.com");
        assertThat(Platform.EUW1.regionalRoute().host()).isEqualTo("europe.api.riotgames.com");
        assertThat(Platform.KR.matchIdPrefix()).isEqualTo("KR_");
        assertThat(Platform.EUN1.matchIdPrefix()).isEqualTo("EUN1_");
    }

    @ParameterizedTest
    @ValueSource(strings = {"euw1", "EUW1", "euw", "EUW", " Euw "})
    void retrouveUnePlateformeParIdentifiantOuAbreviation(String value) {
        assertThat(Platform.find(value)).contains(Platform.EUW1);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "europe", "euw2", "xx"})
    void ignoreLesValeursInconnues(String value) {
        assertThat(Platform.find(value)).isEmpty();
    }

    @Test
    void fromValueLeveUneExceptionSiInconnue() {
        assertThatThrownBy(() -> Platform.fromValue("xx"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("xx");
    }

    @Test
    void retrouveLaPlateformeDUnMatch() {
        assertThat(Platform.fromMatchId("EUW1_7123456789")).contains(Platform.EUW1);
        assertThat(Platform.fromMatchId("KR_7000000000")).contains(Platform.KR);
        // L'abréviation n'est pas un préfixe de match valide
        assertThat(Platform.fromMatchId("EUW_123")).isEmpty();
        assertThat(Platform.fromMatchId("7123456789")).isEmpty();
        assertThat(Platform.fromMatchId("_123")).isEmpty();
        assertThat(Platform.fromMatchId(null)).isEmpty();
    }
}
