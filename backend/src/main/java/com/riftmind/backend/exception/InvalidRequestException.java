package com.riftmind.backend.exception;

/** Requête invalide (paramètre vide, valeur inconnue…) : 400 {@code INVALID_REQUEST}. Le message s'affiche tel quel. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
