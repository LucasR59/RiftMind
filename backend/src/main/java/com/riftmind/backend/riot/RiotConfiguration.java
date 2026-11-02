package com.riftmind.backend.riot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Active {@link RiotProperties}, signale au démarrage la configuration Riot utilisée (clé masquée), et écrit
 * régulièrement dans les logs un résumé des appels à Riot ({@code riot.metrics-log-interval}, 10 min par défaut).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RiotProperties.class)
@EnableScheduling
class RiotConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RiotConfiguration.class);

    private final RiotProperties properties;
    private final RiotMetrics metrics;

    RiotConfiguration(RiotProperties properties, RiotMetrics metrics) {
        this.properties = properties;
        this.metrics = metrics;
    }

    /** Résumé des appels depuis le précédent ; rien si Riot n'a pas été appelé. */
    @Scheduled(fixedDelayString = "${riot.metrics-log-interval:PT10M}",
            initialDelayString = "${riot.metrics-log-interval:PT10M}")
    void logMetricsSummary() {
        String summary = metrics.summaryAndReset();
        if (summary != null) {
            log.info("Appels à l'API Riot depuis le dernier résumé : {}", summary);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    void logConfiguration() {
        log.info("API Riot configurée : clé {}, limites {}, délai entre deux mises à jour {}",
                properties.maskedApiKey(), properties.rateLimit(), properties.refreshCooldown());
        if (properties.baseUrl() != null) {
            log.warn("API Riot SIMULÉE : les appels partent vers {} (tests de bout en bout, RIOT_BASE_URL)",
                    properties.baseUrl());
        }
        if (!properties.apiKey().startsWith("RGAPI-")) {
            log.warn("La clé API Riot ne commence pas par « RGAPI- » : les appels à Riot vont probablement échouer (401/403)");
        }
    }
}
