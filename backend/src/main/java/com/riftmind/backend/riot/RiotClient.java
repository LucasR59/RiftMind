package com.riftmind.backend.riot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Function;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Client bas niveau de l'API Riot : choisit l'hôte (plateforme ou région) et ajoute la clé à chaque requête.
 *
 * <p>Les chemins sont des gabarits d'URI, ex. {@code /riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}} :
 * les variables sont encodées (espaces, {@code /}, {@code #}, accents…), une requête ne peut donc pas être détournée
 * par un Riot ID. Un gabarit peut contenir des paramètres de requête : {@code /ids?start={start}&count={count}}.
 *
 * <p>Toute erreur est convertie en {@link RiotApiException} (voir {@link #toException}) :
 * <ul>
 *   <li>404 → {@link RiotNotFoundException}</li>
 *   <li>429 → {@link RiotRateLimitException} (avec {@code Retry-After})</li>
 *   <li>401 / 403 → {@link RiotApiKeyException}</li>
 *   <li>5xx, timeout, erreur réseau → {@link RiotUnavailableException}</li>
 *   <li>autre code → {@link RiotApiException}</li>
 * </ul>
 * Les limites de requêtes seront appliquées en amont par la tâche 1.5 (voir docs/ROADMAP.md).
 */
public class RiotClient {

    static final String API_KEY_HEADER = "X-Riot-Token";
    static final String RATE_LIMIT_TYPE_HEADER = "X-Rate-Limit-Type";
    private static final int MAX_ERROR_BODY_LENGTH = 300;

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
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw toException(request.getURI().getHost(), response);
                })
                .build();
    }

    /** Appel sur l'hôte d'une plateforme (Summoner-V4, League-V4…), ex. {@code euw1.api.riotgames.com}. */
    public <T> T get(Platform platform, String pathTemplate, Class<T> responseType, Object... uriVariables) {
        return call(platform.host(), pathTemplate, spec -> spec.body(responseType), uriVariables);
    }

    /** Variante pour les types génériques, ex. {@code List<String>}. */
    public <T> T get(Platform platform, String pathTemplate, ParameterizedTypeReference<T> responseType,
            Object... uriVariables) {
        return call(platform.host(), pathTemplate, spec -> spec.body(responseType), uriVariables);
    }

    /** Appel sur l'hôte d'une région de routage (Account-V1, Match-V5), ex. {@code europe.api.riotgames.com}. */
    public <T> T get(RegionalRoute region, String pathTemplate, Class<T> responseType, Object... uriVariables) {
        return call(region.host(), pathTemplate, spec -> spec.body(responseType), uriVariables);
    }

    /** Variante pour les types génériques, ex. {@code List<String>}. */
    public <T> T get(RegionalRoute region, String pathTemplate, ParameterizedTypeReference<T> responseType,
            Object... uriVariables) {
        return call(region.host(), pathTemplate, spec -> spec.body(responseType), uriVariables);
    }

    private <T> T call(String host, String pathTemplate, Function<RestClient.ResponseSpec, T> extractor,
            Object... uriVariables) {
        if (pathTemplate == null || !pathTemplate.startsWith("/")) {
            throw new IllegalArgumentException("Le chemin doit commencer par « / » : " + pathTemplate);
        }
        try {
            // L'hôte provient des enums Platform/RegionalRoute (jamais d'une saisie utilisateur)
            return extractor.apply(restClient.get()
                    .uri("https://" + host + pathTemplate, uriVariables)
                    .retrieve());
        } catch (ResourceAccessException e) {
            // Timeout, connexion refusée, DNS… : Riot n'a pas répondu
            throw new RiotUnavailableException(host, e);
        }
    }

    /** Convertit une réponse d'erreur de Riot en exception métier. */
    static RiotApiException toException(String host, ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        HttpHeaders headers = response.getHeaders();
        return switch (status) {
            case 404 -> new RiotNotFoundException(host, "Introuvable sur " + host + " : " + readBody(response));
            case 429 -> new RiotRateLimitException(host,
                    parseRetryAfter(headers.getFirst(HttpHeaders.RETRY_AFTER)),
                    headers.getFirst(RATE_LIMIT_TYPE_HEADER));
            case 401, 403 -> new RiotApiKeyException(status, host);
            default -> status >= 500
                    ? new RiotUnavailableException(status, host,
                            "Erreur " + status + " de l'API Riot sur " + host + " : " + readBody(response))
                    : new RiotApiException(status, host,
                            "Réponse inattendue " + status + " de " + host + " : " + readBody(response));
        };
    }

    /** {@code Retry-After} en secondes (format utilisé par Riot), ou {@code null} s'il est absent ou invalide. */
    static Duration parseRetryAfter(String value) {
        if (value == null) {
            return null;
        }
        try {
            long seconds = Long.parseLong(value.trim());
            return seconds >= 0 ? Duration.ofSeconds(seconds) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Début du corps de la réponse d'erreur (message de Riot), pour les logs. */
    private static String readBody(ClientHttpResponse response) {
        try {
            byte[] bytes = response.getBody().readNBytes(MAX_ERROR_BODY_LENGTH);
            return new String(bytes, StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
        } catch (IOException e) {
            return "(corps illisible)";
        }
    }
}
