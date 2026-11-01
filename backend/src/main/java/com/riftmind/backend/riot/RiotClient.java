package com.riftmind.backend.riot;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Client bas niveau de l'API Riot : choisit l'hôte (plateforme ou région) et ajoute la clé à chaque requête.
 *
 * <p>Les chemins sont des gabarits d'URI, ex. {@code /riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}} :
 * les variables sont encodées (espaces, {@code /}, {@code #}, accents…), une requête ne peut donc pas être détournée
 * par un Riot ID. Un gabarit peut contenir des paramètres de requête : {@code /ids?start={start}&count={count}}.
 *
 * <p>Les erreurs Riot (404, 429, 403…) seront converties en exceptions métier par la tâche 1.4,
 * et les limites de requêtes appliquées par la tâche 1.5 (voir docs/ROADMAP.md).
 */
public class RiotClient {

    static final String API_KEY_HEADER = "X-Riot-Token";

    private final RestClient restClient;

    /**
     * @param builder    builder fourni par Spring Boot (timeouts déjà configurés dans {@link RiotClientConfiguration}),
     *                   ou relié à un {@code MockRestServiceServer} dans les tests
     * @param properties configuration Riot (clé API)
     */
    public RiotClient(RestClient.Builder builder, RiotProperties properties) {
        this.restClient = builder
                .defaultHeader(API_KEY_HEADER, properties.apiKey())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /** Appel sur l'hôte d'une plateforme (Summoner-V4, League-V4…), ex. {@code euw1.api.riotgames.com}. */
    public <T> T get(Platform platform, String pathTemplate, Class<T> responseType, Object... uriVariables) {
        return retrieve(platform.host(), pathTemplate, uriVariables).body(responseType);
    }

    /** Variante pour les types génériques, ex. {@code List<String>}. */
    public <T> T get(Platform platform, String pathTemplate, ParameterizedTypeReference<T> responseType,
            Object... uriVariables) {
        return retrieve(platform.host(), pathTemplate, uriVariables).body(responseType);
    }

    /** Appel sur l'hôte d'une région de routage (Account-V1, Match-V5), ex. {@code europe.api.riotgames.com}. */
    public <T> T get(RegionalRoute region, String pathTemplate, Class<T> responseType, Object... uriVariables) {
        return retrieve(region.host(), pathTemplate, uriVariables).body(responseType);
    }

    /** Variante pour les types génériques, ex. {@code List<String>}. */
    public <T> T get(RegionalRoute region, String pathTemplate, ParameterizedTypeReference<T> responseType,
            Object... uriVariables) {
        return retrieve(region.host(), pathTemplate, uriVariables).body(responseType);
    }

    private RestClient.ResponseSpec retrieve(String host, String pathTemplate, Object... uriVariables) {
        if (pathTemplate == null || !pathTemplate.startsWith("/")) {
            throw new IllegalArgumentException("Le chemin doit commencer par « / » : " + pathTemplate);
        }
        // L'hôte provient des enums Platform/RegionalRoute (jamais d'une saisie utilisateur)
        return restClient.get()
                .uri("https://" + host + pathTemplate, uriVariables)
                .retrieve();
    }
}
