package com.riftmind.backend.exception;

import com.riftmind.backend.riot.Platform;

/**
 * Le joueur recherché n'existe pas : 404 {@code NOT_FOUND}. Le message est destiné à l'utilisateur.
 */
public class PlayerNotFoundException extends ResourceNotFoundException {

    private PlayerNotFoundException(String message) {
        super(message);
    }

    /** Aucun compte Riot ne porte ce Riot ID. */
    public static PlayerNotFoundException unknownRiotId(String gameName, String tagLine, Platform platform) {
        return new PlayerNotFoundException("Aucun joueur « " + gameName + "#" + tagLine + " » sur "
                + platform.shortName() + " (" + platform.label() + ").");
    }

    /** Aucun joueur enregistré avec ce PUUID : il doit d'abord être recherché par son Riot ID. */
    public static PlayerNotFoundException unknownPuuid(String puuid) {
        return new PlayerNotFoundException("Joueur inconnu de RiftMind (PUUID " + puuid
                + ") : recherchez-le d'abord par son Riot ID.");
    }

    /** Le compte Riot existe, mais n'a pas de profil League of Legends sur cette plateforme. */
    public static PlayerNotFoundException noProfileOnPlatform(String gameName, String tagLine, Platform platform) {
        return new PlayerNotFoundException("Le compte Riot « " + gameName + "#" + tagLine
                + " » n'a pas de profil League of Legends sur " + platform.shortName() + " (" + platform.label()
                + "). Essayez une autre région.");
    }
}
