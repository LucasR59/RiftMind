// Parties (miroir de ParticipantResponse, MatchSummaryResponse, MatchDetailResponse…)

/** Rôle sur la Faille de l'invocateur, ou vide hors Faille classique (ARAM, Arena…). */
export type Position = 'TOP' | 'JUNGLE' | 'MIDDLE' | 'BOTTOM' | 'UTILITY' | ''

/** 100 = équipe bleue, 200 = équipe rouge. */
export type TeamId = number

/** Statistiques complètes d'un joueur dans une partie. Les identifiants sont ceux de Data Dragon. */
export interface Participant {
  /** 1 à 10 (16 en Arena), unique dans la partie */
  participantId: number
  puuid: string
  /** `null` si inconnu (bots, anciennes parties) */
  gameName: string | null
  tagLine: string | null
  teamId: TeamId
  /** `key` du champion dans `champion.json` */
  championId: number
  /** `id` du champion, ex. `KogMaw` : nom de l'image `img/champion/KogMaw.png` */
  championName: string
  champLevel: number
  position: Position
  win: boolean
  kills: number
  deaths: number
  assists: number
  /** Sbires et monstres tués */
  cs: number
  gold: number
  damageChampions: number
  visionScore: number
  /** 7 cases : 6 d'inventaire puis la balise, 0 = case vide */
  items: number[]
  /** 2 sorts d'invocateur (`key` dans `summoner.json`) */
  summonerSpells: number[]
  /** Rune principale (`runesReforged.json`), 0 si absente */
  keystone: number
  /** Arbre de runes secondaire, 0 si absent */
  secondaryStyle: number
}

/** Joueur d'une partie, en version courte pour la carte d'historique. */
export interface CompactParticipant {
  puuid: string
  gameName: string | null
  tagLine: string | null
  teamId: TeamId
  championId: number
  championName: string
}

/** Une partie de l'historique d'un joueur. */
export interface MatchSummary {
  matchId: string
  /** File de jeu (`queues.json` de Data Dragon), ex. 420 = SoloQ */
  queueId: number
  gameVersion: string
  /** Date ISO 8601 du début de la partie */
  gameStart: string
  durationSeconds: number
  /** Partie terminée par un remake : à afficher à part, elle ne compte pas dans les stats */
  remake: boolean
  /** Statistiques du joueur dont c'est l'historique */
  player: Participant
  /** Tous les joueurs de la partie (lui compris), équipe bleue puis rouge */
  participants: CompactParticipant[]
}

/** Une page de l'historique, de la partie la plus récente à la plus ancienne. */
export interface MatchPage {
  items: MatchSummary[]
  /** Numéro de page, à partir de 0 */
  page: number
  size: number
  /** Vrai s'il reste des parties après celle-ci (bouton « Voir plus ») */
  hasMore: boolean
}

/** Totaux d'une équipe dans le détail d'une partie. */
export interface Team {
  teamId: TeamId
  win: boolean
  kills: number
  deaths: number
  assists: number
  gold: number
  damageChampions: number
}

/** Détail complet d'une partie. */
export interface MatchDetail {
  matchId: string
  /** Plateforme du match, ex. `EUW1` */
  platform: string
  queueId: number
  gameVersion: string
  gameStart: string
  durationSeconds: number
  remake: boolean
  /** Bleue (100) puis rouge (200) */
  teams: Team[]
  /** Tous les joueurs, équipe bleue puis rouge */
  participants: Participant[]
}
