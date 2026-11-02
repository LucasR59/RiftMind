-- Rangs classés d'un joueur (tâche 3.1), renvoyés par League-V4 entries/by-puuid/{puuid}.
-- Une ligne = un joueur dans une file classée. Un joueur non classé n'a aucune ligne.
-- Les lignes sont remplacées à chaque rafraîchissement : une file absente de la réponse de Riot est supprimée.

CREATE TABLE league_entry (
    -- Supprimer un joueur supprime ses rangs (demandes de suppression transmises par Riot, tâche 5.8)
    puuid          VARCHAR(100) NOT NULL REFERENCES player (puuid) ON DELETE CASCADE,
    -- Nom de l'enum RankedQueue, identique au queueType de Riot. Seules les files affichées sont gardées.
    -- Nouvelle file dans l'enum = nouvelle migration qui remplace league_entry_queue_type_check.
    queue_type     VARCHAR(32)  NOT NULL
        CONSTRAINT league_entry_queue_type_check CHECK (queue_type IN ('RANKED_SOLO_5x5', 'RANKED_FLEX_SR')),
    -- Palier (IRON … CHALLENGER) et division (I … IV, toujours I à partir de MASTER), tels que renvoyés par Riot.
    -- Pas de contrainte : un nouveau palier côté Riot (comme EMERALD en 2023) ne doit pas faire échouer l'écriture.
    tier           VARCHAR(16)  NOT NULL,
    rank           VARCHAR(4)   NOT NULL,
    league_points  INTEGER      NOT NULL CHECK (league_points >= 0),
    wins           INTEGER      NOT NULL CHECK (wins >= 0),
    losses         INTEGER      NOT NULL CHECK (losses >= 0),
    -- Au moins 3 victoires d'affilée
    hot_streak     BOOLEAN      NOT NULL,
    -- Dernier appel à League-V4 ayant écrit cette ligne
    updated_at     TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (puuid, queue_type)
);
-- La clé primaire commence par puuid : elle sert aussi d'index pour lire tous les rangs d'un joueur.
