package com.riftmind.backend.riot;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Déclare le {@link RiotClient}, avec des timeouts pour qu'un appel à Riot ne bloque jamais indéfiniment. */
@Configuration(proxyBeanMethods = false)
class RiotClientConfiguration {

    /**
     * @param builder builder fourni par Spring Boot (un nouvel exemplaire par injection) : il apporte la
     *                conversion JSON et les métriques {@code http.client.requests}
     */
    @Bean
    RiotClient riotClient(RestClient.Builder builder, RiotProperties properties, RiotRateLimiter rateLimiter) {
        return new RiotClient(builder.requestFactory(requestFactory(properties)), properties, rateLimiter);
    }

    /** Un seul limiteur pour toute l'application : les quotas Riot sont liés à la clé. */
    @Bean
    RiotRateLimiter riotRateLimiter(RiotProperties properties) {
        return new RiotRateLimiter(properties.rateLimit());
    }

    /** Client HTTP du JDK avec les délais de {@code riot.connect-timeout} et {@code riot.read-timeout}. */
    static JdkClientHttpRequestFactory requestFactory(RiotProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return requestFactory;
    }
}
