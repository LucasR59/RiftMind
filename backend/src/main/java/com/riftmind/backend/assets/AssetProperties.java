package com.riftmind.backend.assets;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Copie locale des images et données du jeu (préfixe {@code assets.} dans application.properties).
 *
 * @param directory           dossier de la copie locale ({@code ASSETS_DIR}, {@code data/assets} par défaut)
 * @param ddragonUrl          adresse de Data Dragon ({@code ASSETS_DDRAGON_URL}). Vide : le vrai Data Dragon. Les tests
 *                            de bout en bout la remplacent par la fausse API WireMock
 * @param locale              langue des données de Data Dragon
 * @param syncEnabled         copie automatique des nouvelles versions (au démarrage, puis toutes les
 *                            {@code syncInterval}). Désactivée dans les tests
 * @param syncInterval        intervalle entre deux vérifications d'une nouvelle version de Data Dragon
 * @param keepVersions        nombre de versions gardées sur le disque (les plus récentes) ; la précédente reste
 *                            utile aux onglets ouverts pendant un changement de patch
 * @param downloadConcurrency téléchargements simultanés pendant la copie d'une version
 * @param connectTimeout      délai maximum de connexion à Data Dragon
 * @param readTimeout         délai maximum d'attente d'une réponse, quand un visiteur attend (icône de profil
 *                            téléchargée à la première demande)
 * @param syncReadTimeout     délai maximum d'attente d'une réponse pendant la copie, en tâche de fond
 */
@Validated
@ConfigurationProperties("assets")
public record AssetProperties(
        @NotBlank @DefaultValue("data/assets") String directory,
        @Pattern(regexp = "https?://\\S+", message = "doit être une adresse http(s)") String ddragonUrl,
        @NotNull @Pattern(regexp = "[a-z]{2}_[A-Z]{2}") @DefaultValue("fr_FR") String locale,
        @DefaultValue("true") boolean syncEnabled,
        @NotNull @DefaultValue("PT1H") Duration syncInterval,
        @Min(1) @Max(10) @DefaultValue("2") int keepVersions,
        @Min(1) @Max(32) @DefaultValue("8") int downloadConcurrency,
        @NotNull @DefaultValue("5s") Duration connectTimeout,
        @NotNull @DefaultValue("10s") Duration readTimeout,
        @NotNull @DefaultValue("60s") Duration syncReadTimeout) {

    public static final String DEFAULT_DDRAGON_URL = "https://ddragon.leagueoflegends.com";

    /** Une adresse vide revient au vrai Data Dragon ; la barre oblique finale est retirée. */
    public AssetProperties {
        ddragonUrl = ddragonUrl == null || ddragonUrl.isBlank()
                ? DEFAULT_DDRAGON_URL
                : ddragonUrl.strip().replaceAll("/+$", "");
    }

    /** Configuration par défaut avec un autre dossier et une autre adresse (tests). */
    public static AssetProperties of(Path directory, String ddragonUrl) {
        return new AssetProperties(directory.toString(), ddragonUrl, "fr_FR", false, Duration.ofHours(1), 2, 4,
                Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(60));
    }

    /** Dossier de la copie locale, en chemin absolu. */
    public Path root() {
        return Path.of(directory).toAbsolutePath().normalize();
    }

    /** Vrai si Data Dragon est remplacé (fausse API des tests de bout en bout). */
    public boolean simulated() {
        return !DEFAULT_DDRAGON_URL.equals(ddragonUrl);
    }
}
