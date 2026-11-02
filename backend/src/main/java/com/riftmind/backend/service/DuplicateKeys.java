package com.riftmind.backend.service;

import java.sql.SQLException;

import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

/** Reconnaît, parmi les erreurs d'intégrité, celles dues à une écriture simultanée de la même ligne. */
final class DuplicateKeys {

    /** SQLState de PostgreSQL pour une violation de contrainte d'unicité. */
    private static final String UNIQUE_VIOLATION = "23505";

    private DuplicateKeys() {
    }

    /**
     * Vrai pour une clé déjà présente (SQLState 23505), faux pour une autre contrainte (CHECK, clé étrangère…).
     * Selon le chemin (JDBC ou JPA), Spring renvoie un {@link DuplicateKeyException} ou un
     * {@link DataIntegrityViolationException} plus général : les deux sont reconnus.
     */
    static boolean isDuplicateKey(DataIntegrityViolationException e) {
        return e instanceof DuplicateKeyException
                || NestedExceptionUtils.getMostSpecificCause(e) instanceof SQLException sql
                        && UNIQUE_VIOLATION.equals(sql.getSQLState());
    }
}
