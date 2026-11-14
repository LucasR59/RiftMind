-- Historique des rangs d'un joueur (tâche 7.10), pour le graphique d'évolution et le peak.
-- Riot ne donne ni le MMR ni les rangs passés : seulement le rang au moment de l'appel (League-V4). RiftMind garde
-- donc une ligne à chaque fois qu'un rang change (recherche d'un nouveau joueur, bouton « Mettre à jour »).
-- Un rang identique au précédent n'ajoute pas de ligne.

CREATE TABLE rank_snapshot (
    id             BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Supprimer un joueur supprime son historique (demandes de suppression transmises par Riot, tâche 5.8)
    puuid          VARCHAR(100) NOT NULL REFERENCES player (puuid) ON DELETE CASCADE,
    -- Mêmes valeurs que league_entry.queue_type (enum RankedQueue)
    queue_type     VARCHAR(32)  NOT NULL
        CONSTRAINT rank_snapshot_queue_type_check CHECK (queue_type IN ('RANKED_SOLO_5x5', 'RANKED_FLEX_SR')),
    -- Palier et division tels que renvoyés par Riot, sans contrainte (comme league_entry)
    tier           VARCHAR(16)  NOT NULL,
    rank           VARCHAR(4)   NOT NULL,
    league_points  INTEGER      NOT NULL CHECK (league_points >= 0),
    wins           INTEGER      NOT NULL CHECK (wins >= 0),
    losses         INTEGER      NOT NULL CHECK (losses >= 0),
    -- Instant de l'appel à League-V4 qui a renvoyé ce rang
    recorded_at    TIMESTAMPTZ  NOT NULL
);

-- Lecture de l'historique d'un joueur, file par file, dans l'ordre chronologique ; et dernier rang connu d'une file
CREATE INDEX rank_snapshot_puuid_queue_recorded_idx ON rank_snapshot (puuid, queue_type, recorded_at);

-- Point de départ : le rang actuel des joueurs déjà connus
INSERT INTO rank_snapshot (puuid, queue_type, tier, rank, league_points, wins, losses, recorded_at)
SELECT puuid, queue_type, tier, rank, league_points, wins, losses, updated_at
FROM league_entry;
