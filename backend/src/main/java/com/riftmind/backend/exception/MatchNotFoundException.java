package com.riftmind.backend.exception;

/** La partie demandée n'est pas enregistrée sur RiftMind : 404 {@code NOT_FOUND}. Message destiné à l'utilisateur. */
public class MatchNotFoundException extends ResourceNotFoundException {

    public MatchNotFoundException(String matchId) {
        super("Partie « " + matchId + " » introuvable.");
    }
}
