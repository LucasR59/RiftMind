package com.riftmind.backend.exception;

/** À lever quand une ressource demandée n'existe pas : renvoie une 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
