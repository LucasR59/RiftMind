package com.riftmind.backend.assets;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestClient;

/**
 * Déclare la copie locale des images du jeu, et sa mise à jour automatique ({@code assets.sync-enabled}) : au
 * démarrage, puis toutes les {@code assets.sync-interval}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AssetProperties.class)
class AssetConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AssetConfiguration.class);

    /** La copie locale, avec les emblèmes de rang livrés avec le backend (installés tout de suite, sans réseau). */
    @Bean
    AssetStore assetStore(AssetProperties properties) {
        AssetStore store = new AssetStore(properties.root());
        try {
            int written = RankImages.install(store);
            if (written > 0) {
                log.info("Emblèmes de rang installés dans la copie locale : {} fichiers", written);
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Emblèmes de rang non installés dans {} : {}", store.root(), e.toString());
        }
        return store;
    }

    /**
     * Copie en tâche de fond : délai de réponse long ({@code assets.sync-read-timeout}).
     *
     * @param builder builder fourni par Spring Boot (un nouvel exemplaire par injection, sans en-tête par défaut :
     *                rien de commun avec le client de l'API Riot et sa clé)
     */
    @Bean
    AssetSync assetSync(AssetProperties properties, AssetStore store, RestClient.Builder builder) {
        return new AssetSync(properties, store, downloader(builder, properties, properties.syncReadTimeout()));
    }

    /** Fichiers servis : un visiteur peut attendre une icône de profil, délai court ({@code assets.read-timeout}). */
    @Bean
    AssetService assetService(AssetProperties properties, AssetStore store, RestClient.Builder builder, Clock clock) {
        return new AssetService(properties, store, downloader(builder, properties, properties.readTimeout()), clock);
    }

    /** Client HTTP du JDK : délais de {@code assets.*-timeout}, redirections refusées. Utilisé aussi par les tests. */
    static AssetDownloader downloader(RestClient.Builder builder, AssetProperties properties, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return new AssetDownloader(builder.requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, "RiftMind")
                .build());
    }

    @Bean
    @ConditionalOnProperty(name = "assets.sync-enabled", havingValue = "true", matchIfMissing = true)
    AssetSyncScheduler assetSyncScheduler(AssetSync sync, AssetProperties properties, AssetStore store) {
        return new AssetSyncScheduler(sync, properties, store);
    }

    /** Lance la copie au démarrage (sans le retarder), puis à intervalle régulier. */
    static class AssetSyncScheduler {

        private static final Logger log = LoggerFactory.getLogger(AssetSyncScheduler.class);

        private final AssetSync sync;

        AssetSyncScheduler(AssetSync sync, AssetProperties properties, AssetStore store) {
            this.sync = sync;
            log.info("Images du jeu : copie locale dans {}, versions de Data Dragon disponibles : {}",
                    store.root(), store.publishedVersions());
            if (properties.simulated()) {
                log.warn("Images du jeu SIMULÉES : copiées depuis {} (tests de bout en bout)", properties.ddragonUrl());
            }
        }

        @Scheduled(fixedDelayString = "${assets.sync-interval:PT1H}", initialDelayString = "PT0S")
        void sync() {
            try {
                sync.sync();
            } catch (RuntimeException e) {
                log.warn("Copie des images du jeu en échec, nouvel essai plus tard", e);
            }
        }
    }
}
