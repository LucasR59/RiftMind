-- Migration initiale (Flyway).
-- Chaque évolution du schéma = un nouveau fichier V<n>__<description>.sql, jamais modifier un fichier déjà appliqué.

CREATE TABLE IF NOT EXISTS app_info (
    key        VARCHAR(100) PRIMARY KEY,
    value      VARCHAR(255) NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO app_info (key, value) VALUES ('schema.initialized', 'true')
ON CONFLICT (key) DO NOTHING;
