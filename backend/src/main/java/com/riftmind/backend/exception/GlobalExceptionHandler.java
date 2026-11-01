package com.riftmind.backend.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.riftmind.backend.riot.Platform;
import com.riftmind.backend.riot.RiotApiException;
import com.riftmind.backend.riot.RiotApiKeyException;
import com.riftmind.backend.riot.RiotNotFoundException;
import com.riftmind.backend.riot.RiotRateLimitException;
import com.riftmind.backend.riot.RiotUnavailableException;

/**
 * Centralise la conversion des exceptions en réponses d'erreur au format RFC 9457 (application/problem+json).
 *
 * <p>Chaque réponse porte une propriété {@code code} stable, utilisable par le front pour choisir le message
 * à afficher (voir {@link ErrorCode}).
 *
 * <p>{@code @Order} le fait passer avant le gestionnaire par défaut de Spring Boot ({@code ProblemDetailsExceptionHandler}).
 * Sans cela, ce dernier traiterait par exemple une plateforme inconnue dans l'URL, sans la propriété {@code code}.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Codes d'erreur renvoyés dans la propriété {@code code} des réponses. */
    public enum ErrorCode {
        INVALID_REQUEST,
        NOT_FOUND,
        RIOT_RATE_LIMITED,
        RIOT_UNAVAILABLE,
        RIOT_ERROR
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ProblemDetail handleInvalidRequest(InvalidRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, "Requête invalide", ex.getMessage());
    }

    /** Valeur de l'URL impossible à convertir, ex. une plateforme inconnue dans {@code /api/players/xx/...}. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String detail = ex.getRequiredType() == Platform.class
                ? "Plateforme inconnue : « " + ex.getValue() + " ». Valeurs acceptées : " + Platform.acceptedValues()
                        + "."
                : "Valeur invalide pour « " + ex.getName() + " » : « " + ex.getValue() + " ».";
        return problem(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, "Requête invalide", detail);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Ressource introuvable", ex.getMessage());
    }

    @ExceptionHandler(RiotNotFoundException.class)
    public ProblemDetail handleRiotNotFound(RiotNotFoundException ex) {
        log.debug("{}", ex.getMessage());
        return problem(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Ressource introuvable",
                "La ressource demandée n'existe pas sur les serveurs Riot.");
    }

    /**
     * Limite Riot atteinte : ce n'est pas la faute de l'utilisateur → 503 avec {@code Retry-After}
     * (le 429 est réservé aux limites propres à RiftMind, ex. délai entre deux mises à jour).
     */
    @ExceptionHandler(RiotRateLimitException.class)
    public ResponseEntity<ProblemDetail> handleRiotRateLimit(RiotRateLimitException ex) {
        long seconds = Math.max(1, (ex.retryAfter().toMillis() + 999) / 1000);
        log.warn("{} : nouvel essai possible dans {} s", ex.getMessage(), seconds);
        ProblemDetail body = problem(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.RIOT_RATE_LIMITED,
                "Trop de requêtes vers Riot",
                "Les serveurs Riot limitent temporairement les requêtes. Réessayez dans " + seconds + " s.");
        body.setProperty("retryAfterSeconds", seconds);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
                .body(body);
    }

    /** Clé refusée : problème de configuration du serveur, détaillé uniquement dans les logs. */
    @ExceptionHandler(RiotApiKeyException.class)
    public ProblemDetail handleRiotApiKey(RiotApiKeyException ex) {
        log.error("{}", ex.getMessage());
        return riotUnavailable();
    }

    @ExceptionHandler(RiotUnavailableException.class)
    public ProblemDetail handleRiotUnavailable(RiotUnavailableException ex) {
        log.warn("{}", ex.getMessage());
        return riotUnavailable();
    }

    /** Réponse inattendue de Riot (400, 415…) : sans doute un bug dans notre appel. */
    @ExceptionHandler(RiotApiException.class)
    public ProblemDetail handleRiotApi(RiotApiException ex) {
        log.error("{}", ex.getMessage(), ex);
        return problem(HttpStatus.BAD_GATEWAY, ErrorCode.RIOT_ERROR, "Erreur de l'API Riot",
                "Une erreur inattendue est survenue en interrogeant les serveurs Riot.");
    }

    private static ProblemDetail riotUnavailable() {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.RIOT_UNAVAILABLE, "Serveurs Riot indisponibles",
                "Les données de League of Legends sont temporairement indisponibles. Réessayez plus tard.");
    }

    private static ProblemDetail problem(HttpStatus status, ErrorCode code, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code.name());
        return problem;
    }
}
