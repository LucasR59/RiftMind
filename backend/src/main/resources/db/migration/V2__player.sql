-- Joueurs recherchés sur RiftMind (tâche 2.2).
-- Une ligne = un compte Riot, identifié par son PUUID. Le Riot ID et le niveau sont mis à jour à chaque rafraîchissement.

CREATE TABLE player (
    -- Identifiant stable du compte (78 caractères aujourd'hui, marge si Riot change le format)
    puuid             VARCHAR(100) PRIMARY KEY,
    -- Riot ID « game_name#tag_line ». Riot limite gameName à 16 caractères et tagLine à 5 (marge gardée).
    -- Il peut changer : ce n'est donc pas une clé. Deux lignes peuvent porter le même Riot ID quand un joueur a
    -- libéré un nom repris par un autre et que l'ancienne ligne n'a pas encore été rafraîchie.
    game_name         VARCHAR(64)  NOT NULL,
    tag_line          VARCHAR(16)  NOT NULL,
    -- Plateforme où le joueur a été trouvé : nom de l'enum Platform (EUW1, NA1, KR…), comme info.platformId des matchs.
    -- Nouvelle plateforme dans l'enum = nouvelle migration qui remplace player_platform_check
    -- (PlayerMigrationTest échoue sinon).
    platform          VARCHAR(8)   NOT NULL
        CONSTRAINT player_platform_check CHECK (platform IN (
            'BR1', 'LA1', 'LA2', 'NA1',
            'EUN1', 'EUW1', 'ME1', 'RU', 'TR1',
            'JP1', 'KR',
            'OC1', 'SG2', 'TW2', 'VN2')),
    summoner_level    INTEGER      NOT NULL CHECK (summoner_level >= 0),
    -- Icône Data Dragon : img/profileicon/{profile_icon_id}.png
    profile_icon_id   INTEGER      NOT NULL CHECK (profile_icon_id >= 0),
    -- Dernier appel à Riot pour ce joueur : sert au délai minimum entre deux mises à jour (riot.refresh-cooldown)
    last_refreshed_at TIMESTAMPTZ  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Recherche par Riot ID, insensible à la casse comme chez Riot. Les requêtes doivent avoir cette forme pour utiliser
-- l'index :
--   WHERE platform = ? AND lower(game_name) = lower(?) AND lower(tag_line) = lower(?)
CREATE INDEX player_riot_id_idx ON player (platform, lower(game_name), lower(tag_line));
