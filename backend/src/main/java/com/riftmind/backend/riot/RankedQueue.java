package com.riftmind.backend.riot;

import java.util.Arrays;
import java.util.Optional;

/**
 * File classée affichée par RiftMind. Le nom de chaque constante est exactement le {@code queueType} de League-V4,
 * et c'est lui qui est stocké en base ({@code league_entry.queue_type}, V3__league_entry.sql).
 *
 * <p>L'ordre des constantes est l'ordre d'affichage : SoloQ, puis Flex.
 * Ajouter une file ici impose une migration qui remplace {@code league_entry_queue_type_check}
 * ({@code LeagueEntryMigrationTest} échoue sinon).
 */
public enum RankedQueue {

    /** Classée solo/duo, file 420 de Match-V5. */
    RANKED_SOLO_5x5("Classée Solo/Duo"),
    /** Classée flexible, file 440 de Match-V5. */
    RANKED_FLEX_SR("Classée Flexible");

    private final String label;

    RankedQueue(String label) {
        this.label = label;
    }

    /** Nom affiché, ex. {@code Classée Solo/Duo}. */
    public String label() {
        return label;
    }

    /**
     * File correspondant au {@code queueType} renvoyé par Riot, ou vide si RiftMind ne l'affiche pas (ancienne file
     * comme {@code RANKED_FLEX_TT}, ou file ajoutée par Riot).
     */
    public static Optional<RankedQueue> fromRiot(String queueType) {
        return Arrays.stream(values()).filter(queue -> queue.name().equals(queueType)).findFirst();
    }
}
