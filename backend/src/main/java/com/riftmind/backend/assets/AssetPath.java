package com.riftmind.backend.assets;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;

/**
 * Chemin autorisé sous {@code /api/assets/}. C'est la <b>seule</b> liste blanche de la copie locale : un chemin qui
 * n'est pas reconnu ici n'est ni lu sur le disque ni demandé à Data Dragon. Les mêmes règles valident les noms lus
 * dans les fichiers de Data Dragon pendant la copie.
 *
 * <p>Les chemins de Data Dragon sont repris tels quels, préfixés par {@code ddragon/} :
 * <ul>
 *   <li>{@code ddragon/versions.json} : versions copiées, la plus récente d'abord ;</li>
 *   <li>{@code ddragon/cdn/{version}/data/fr_FR/{champion|item|summoner|runesReforged}.json} ;</li>
 *   <li>{@code ddragon/cdn/{version}/img/{champion|item|spell|profileicon}/{nom}.png} ;</li>
 *   <li>{@code ddragon/cdn/img/perk-images/…/{nom}.png} (runes, non versionnées) ;</li>
 *   <li>{@code ranks/{palier}.png} : emblèmes de rang, livrés avec le backend (voir {@link #rankImages()}).</li>
 * </ul>
 * Chaque segment est limité à des lettres, chiffres, {@code _} et {@code -} : pas de {@code ..}, de {@code \}, de
 * {@code %} ni de fichier caché.
 *
 * @param path    chemin relatif, ex. {@code ddragon/cdn/16.21.1/img/champion/Kalista.png}
 * @param kind    type de fichier
 * @param version version de Data Dragon, ou {@code null} pour un fichier non versionné
 */
public record AssetPath(String path, Kind kind, String version) {

    /** Version de Data Dragon, ex. {@code 16.21.1}. */
    static final Pattern VERSION = Pattern.compile("\\d{1,4}\\.\\d{1,4}\\.\\d{1,4}");

    /** Fichiers de données copiés, dans l'ordre de téléchargement. */
    static final List<String> DATA_FILES = List.of("champion", "item", "summoner", "runesReforged");

    /** Paliers classés, en minuscules (noms des images de rang). */
    static final List<String> TIERS = List.of("iron", "bronze", "silver", "gold", "platinum", "emerald", "diamond",
            "master", "grandmaster", "challenger");

    /** Tri des versions de la plus récente à la plus ancienne : {@code 16.21.1} avant {@code 16.9.1}. */
    static final Comparator<String> NEWEST_FIRST = Comparator.comparing(AssetPath::versionKey).reversed();

    private static final String V = "(?<version>\\d{1,4}\\.\\d{1,4}\\.\\d{1,4})";
    private static final String TIER = String.join("|", TIERS);

    private static final Pattern DATA = Pattern.compile(
            "ddragon/cdn/" + V + "/data/(?<locale>[a-z]{2}_[A-Z]{2})/(champion|item|summoner|runesReforged)\\.json");
    private static final Pattern CHAMPION = Pattern.compile("ddragon/cdn/" + V + "/img/champion/[A-Za-z0-9]{1,40}\\.png");
    private static final Pattern ITEM = Pattern.compile("ddragon/cdn/" + V + "/img/item/\\d{1,6}\\.png");
    private static final Pattern SPELL = Pattern.compile("ddragon/cdn/" + V + "/img/spell/[A-Za-z0-9_]{1,60}\\.png");
    private static final Pattern PROFILE_ICON = Pattern.compile("ddragon/cdn/" + V + "/img/profileicon/\\d{1,7}\\.png");
    private static final Pattern RUNE = Pattern.compile("ddragon/cdn/img/perk-images(/[A-Za-z0-9_-]{1,60}){1,6}\\.png");
    private static final Pattern RANK = Pattern.compile("ranks/(" + TIER + "|unranked)\\.png");

    static final String VERSIONS = "ddragon/versions.json";

    /** Type de fichier : format, durée de cache, et s'il peut être téléchargé à la première demande. */
    public enum Kind {
        /** Liste des versions copiées : relue souvent pour voir passer un nouveau patch. */
        VERSIONS(MediaType.APPLICATION_JSON, Duration.ofMinutes(5), false),
        /** Données d'une version : ne changent jamais. */
        DATA(MediaType.APPLICATION_JSON, Duration.ofDays(365), true),
        /** Image versionnée (champion, objet, sort) : ne change jamais. */
        IMAGE(MediaType.IMAGE_PNG, Duration.ofDays(365), true),
        /** Icône de profil : versionnée, téléchargée à la première demande (il en existe des milliers). */
        PROFILE_ICON(MediaType.IMAGE_PNG, Duration.ofDays(365), true),
        /** Image de rune, non versionnée : gardée un jour par le navigateur, puis revalidée. */
        STATIC_IMAGE(MediaType.IMAGE_PNG, Duration.ofDays(1), false),
        /** Emblème de rang, livré avec le backend (jamais téléchargé) : gardé un jour, puis revalidé. */
        RANK(MediaType.IMAGE_PNG, Duration.ofDays(1), false);

        private final MediaType mediaType;
        private final Duration maxAge;
        private final boolean immutable;

        Kind(MediaType mediaType, Duration maxAge, boolean immutable) {
            this.mediaType = mediaType;
            this.maxAge = maxAge;
            this.immutable = immutable;
        }

        public MediaType mediaType() {
            return mediaType;
        }

        /** {@code public, max-age=…}, avec {@code immutable} pour un fichier versionné. */
        public CacheControl cacheControl() {
            CacheControl cacheControl = CacheControl.maxAge(maxAge).cachePublic();
            return immutable ? cacheControl.immutable() : cacheControl;
        }
    }

    /**
     * Reconnaît un chemin de la liste blanche.
     *
     * @param path   chemin relatif, sans {@code /} au début
     * @param locale seule langue servie, ex. {@code fr_FR}
     * @return le chemin reconnu, ou vide s'il n'est pas autorisé
     */
    public static Optional<AssetPath> parse(String path, String locale) {
        if (path == null || path.length() > 200) {
            return Optional.empty();
        }
        if (path.equals(VERSIONS)) {
            return Optional.of(new AssetPath(path, Kind.VERSIONS, null));
        }
        Matcher data = DATA.matcher(path);
        if (data.matches()) {
            return data.group("locale").equals(locale)
                    ? Optional.of(new AssetPath(path, Kind.DATA, data.group("version")))
                    : Optional.empty();
        }
        for (Pattern image : List.of(CHAMPION, ITEM, SPELL)) {
            Matcher matcher = image.matcher(path);
            if (matcher.matches()) {
                return Optional.of(new AssetPath(path, Kind.IMAGE, matcher.group("version")));
            }
        }
        Matcher profileIcon = PROFILE_ICON.matcher(path);
        if (profileIcon.matches()) {
            return Optional.of(new AssetPath(path, Kind.PROFILE_ICON, profileIcon.group("version")));
        }
        if (RUNE.matcher(path).matches()) {
            return Optional.of(new AssetPath(path, Kind.STATIC_IMAGE, null));
        }
        if (RANK.matcher(path).matches()) {
            return Optional.of(new AssetPath(path, Kind.RANK, null));
        }
        return Optional.empty();
    }

    /**
     * Les 11 emblèmes de rang ({@code ranks/{palier}.png}, avec {@code unranked}). Ils sont livrés avec le backend
     * ({@code src/main/resources/assets/ranks/}, tirés des emblèmes officiels de Riot par
     * {@code scripts/RankImages.java}) et installés dans la copie locale au démarrage : aucun téléchargement.
     */
    static List<String> rankImages() {
        return Stream.concat(TIERS.stream(), Stream.of("unranked")).map(tier -> "ranks/" + tier + ".png").toList();
    }

    static String dataPath(String version, String locale, String file) {
        return "ddragon/cdn/" + version + "/data/" + locale + "/" + file + ".json";
    }

    static String imagePath(String version, String folder, String file) {
        return "ddragon/cdn/" + version + "/img/" + folder + "/" + file;
    }

    /** Dossier d'une version : {@code ddragon/cdn/{version}}. */
    static String versionDirectory(String version) {
        return "ddragon/cdn/" + version;
    }

    static boolean isVersion(String value) {
        return value != null && VERSION.matcher(value).matches();
    }

    /** Adresse du fichier chez Data Dragon. Le chemin est déjà validé : rien à encoder. */
    String upstreamUrl(AssetProperties properties) {
        if (kind == Kind.RANK) {
            throw new IllegalStateException("Les emblèmes de rang ne sont jamais téléchargés : " + path);
        }
        if (kind == Kind.VERSIONS) {
            // Chez Data Dragon : toutes les versions publiées ; ici : celles qui sont copiées
            return properties.ddragonUrl() + "/api/versions.json";
        }
        return properties.ddragonUrl() + path.substring("ddragon".length());
    }

    private static long versionKey(String version) {
        long key = 0;
        for (String part : version.split("\\.")) {
            key = key * 10_000 + Long.parseLong(part);
        }
        return key;
    }
}
