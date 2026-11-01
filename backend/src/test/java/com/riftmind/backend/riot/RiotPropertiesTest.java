package com.riftmind.backend.riot;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.riftmind.backend.riot.RiotProperties.Window;

class RiotPropertiesTest {

    private static final String FAKE_KEY = "RGAPI-00000000-1111-2222-3333-444444444444";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(RiotConfiguration.class));

    @Test
    void appliqueLesValeursParDefautDUneCleDeDev() {
        runner.withPropertyValues("riot.api-key=" + FAKE_KEY).run(context -> {
            RiotProperties props = context.getBean(RiotProperties.class);

            assertThat(props.apiKey()).isEqualTo(FAKE_KEY);
            assertThat(props.rateLimit().shortWindow()).isEqualTo(new Window(20, Duration.ofSeconds(1)));
            assertThat(props.rateLimit().longWindow()).isEqualTo(new Window(100, Duration.ofMinutes(2)));
            assertThat(props.refreshCooldown()).isEqualTo(Duration.ofMinutes(2));
        });
    }

    @Test
    void accepteLesLimitesDUneCleDeProduction() {
        runner.withPropertyValues(
                "riot.api-key=" + FAKE_KEY,
                "riot.rate-limit.short-window.requests=500",
                "riot.rate-limit.short-window.period=10s",
                "riot.rate-limit.long-window.requests=30000",
                "riot.rate-limit.long-window.period=10m",
                "riot.refresh-cooldown=5m").run(context -> {
            RiotProperties props = context.getBean(RiotProperties.class);

            assertThat(props.rateLimit().shortWindow()).isEqualTo(new Window(500, Duration.ofSeconds(10)));
            assertThat(props.rateLimit().longWindow()).isEqualTo(new Window(30000, Duration.ofMinutes(10)));
            assertThat(props.refreshCooldown()).isEqualTo(Duration.ofMinutes(5));
        });
    }

    @Test
    void refuseDeDemarrerSansCle() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("RIOT_API_KEY");
        });
    }

    @Test
    void refuseDeDemarrerAvecUneCleVide() {
        runner.withPropertyValues("riot.api-key=  ").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void refuseUneLimiteNulle() {
        runner.withPropertyValues("riot.api-key=" + FAKE_KEY, "riot.rate-limit.short-window.requests=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void neJamaisAfficherLaCleEnClair() {
        RiotProperties props = new RiotProperties(FAKE_KEY, new RiotProperties.RateLimit(null, null), Duration.ofMinutes(2));

        assertThat(props.toString()).doesNotContain(FAKE_KEY).contains("RGAPI-****(42 caractères)");
        assertThat(props.maskedApiKey()).doesNotContain("0000");
        assertThat(props.rateLimit().toString()).contains("20 req/1s").contains("100 req/2m");
    }
}
