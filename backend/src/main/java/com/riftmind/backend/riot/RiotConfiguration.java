package com.riftmind.backend.riot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/** Active {@link RiotProperties} et signale au démarrage la configuration Riot utilisée (clé masquée). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RiotProperties.class)
class RiotConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RiotConfiguration.class);

    private final RiotProperties properties;

    RiotConfiguration(RiotProperties properties) {
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    void logConfiguration() {
        log.info("API Riot configurée : clé {}, limites {}, délai entre deux mises à jour {}",
                properties.maskedApiKey(), properties.rateLimit(), properties.refreshCooldown());
        if (!properties.apiKey().startsWith("RGAPI-")) {
            log.warn("La clé API Riot ne commence pas par « RGAPI- » : les appels à Riot vont probablement échouer (401/403)");
        }
    }
}
