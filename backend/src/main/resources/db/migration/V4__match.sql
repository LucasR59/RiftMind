-- Parties récupérées chez Riot (tâche 4.2), via Match-V5 matches/{matchId}.
-- Une partie terminée ne change jamais : elle est écrite une seule fois, puis seulement lue.

CREATE TABLE match (
    -- Identifiant Riot, préfixé par la plateforme, ex. EUW1_7000000001
    match_id      VARCHAR(32)  PRIMARY KEY,
    -- info.platformId tel que renvoyé par Riot (EUW1, KR…). Pas de contrainte, contrairement à player.platform :
    -- d'anciennes parties peuvent venir de plateformes fermées depuis (PH2, TH2, fusionnées dans SG2).
    platform      VARCHAR(8)   NOT NULL,
    -- File de jeu (queues.json de Data Dragon) : 420 = SoloQ, 440 = Flex, 450 = ARAM, 0 = partie personnalisée…
    queue_id      INTEGER      NOT NULL,
    -- Version du client, ex. 16.21.834.2754 (patch 16.21)
    game_version  VARCHAR(32)  NOT NULL,
    game_start    TIMESTAMPTZ  NOT NULL,
    duration_s    INTEGER      NOT NULL CHECK (duration_s >= 0),
    -- Partie terminée par un remake (tâche 4.5) : affichée dans l'historique, mais exclue des statistiques
    remake        BOOLEAN      NOT NULL DEFAULT false,
    -- Réponse brute de Riot, gardée pour les besoins futurs. NULL si elle ne doit pas être conservée
    -- (parties personnalisées, tâche 4.6).
    raw           JSONB,
    -- Date d'enregistrement chez RiftMind
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Statistiques d'un joueur dans une partie : 10 lignes par partie (16 en Arena).
-- game_start et queue_id sont recopiés depuis match (qui ne change jamais) : l'historique d'un joueur se lit ainsi
-- avec le seul index match_participant_puuid_game_start_idx, sans jointure pour trier ni filtrer par file.
CREATE TABLE match_participant (
    match_id          VARCHAR(32)  NOT NULL REFERENCES match (match_id) ON DELETE CASCADE,
    -- 1 à 10 (16 en Arena). Clé avec match_id : le PUUID n'est pas unique dans une partie (bots contre l'IA).
    participant_id    INTEGER      NOT NULL,
    -- Pas de clé étrangère vers player : la plupart des participants n'ont jamais été recherchés sur RiftMind
    puuid             VARCHAR(100) NOT NULL,
    -- Riot ID au moment de la partie (absent pour les bots et dans certaines anciennes parties)
    game_name         VARCHAR(64),
    tag_line          VARCHAR(16),
    -- 100 = bleu, 200 = rouge
    team_id           INTEGER      NOT NULL,
    -- Champion : key (numérique) et id (texte, ex. KogMaw) de Data Dragon
    champion_id       INTEGER      NOT NULL,
    champion_name     VARCHAR(32)  NOT NULL,
    champ_level       INTEGER      NOT NULL,
    -- TOP, JUNGLE, MIDDLE, BOTTOM, UTILITY, ou '' hors Faille classique (ARAM, Arena…)
    position          VARCHAR(16)  NOT NULL,
    win               BOOLEAN      NOT NULL,
    kills             INTEGER      NOT NULL,
    deaths            INTEGER      NOT NULL,
    assists           INTEGER      NOT NULL,
    -- Sbires + monstres de la jungle
    cs                INTEGER      NOT NULL,
    gold              INTEGER      NOT NULL,
    damage_champions  INTEGER      NOT NULL,
    vision_score      INTEGER      NOT NULL,
    -- 6 cases d'inventaire puis la balise, 0 = case vide
    items             INTEGER[]    NOT NULL CHECK (cardinality(items) = 7),
    summoner_spells   INTEGER[]    NOT NULL CHECK (cardinality(summoner_spells) = 2),
    -- Rune principale et arbre secondaire (runesReforged.json), 0 si absents
    keystone          INTEGER      NOT NULL,
    secondary_style   INTEGER      NOT NULL,
    -- Copies de match.game_start et match.queue_id (voir plus haut)
    game_start        TIMESTAMPTZ  NOT NULL,
    queue_id          INTEGER      NOT NULL,
    PRIMARY KEY (match_id, participant_id)
);

-- Historique d'un joueur, du plus récent au plus ancien (tâche 5.1)
CREATE INDEX match_participant_puuid_game_start_idx ON match_participant (puuid, game_start DESC);
-- Statistiques par champion d'un joueur (tâche 5.3)
CREATE INDEX match_participant_puuid_champion_idx ON match_participant (puuid, champion_id);
