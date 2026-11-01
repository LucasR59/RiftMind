package com.riftmind.backend.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import com.riftmind.backend.riot.Platform;

/**
 * Convertit la plateforme d'une URL en {@link Platform}. Accepte l'identifiant Riot ({@code euw1}) ou l'abréviation
 * ({@code EUW}), sans tenir compte de la casse. Spring Boot enregistre automatiquement les {@link Converter} déclarés
 * comme beans.
 *
 * <p>Une valeur inconnue devient une 400 {@code INVALID_REQUEST} (voir {@code GlobalExceptionHandler}).
 */
@Component
public class PlatformConverter implements Converter<String, Platform> {

    @Override
    public Platform convert(String source) {
        return Platform.fromValue(source);
    }
}
