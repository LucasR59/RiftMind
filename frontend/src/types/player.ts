// Joueurs et rangs (miroir de PlayerResponse et LeagueEntryResponse)

/** File classée : SoloQ ou Flex, comme le `queueType` de Riot. */
export type RankedQueue = 'RANKED_SOLO_5x5' | 'RANKED_FLEX_SR'

/** Rang d'un joueur dans une file classée. */
export interface LeagueEntry {
  queueType: RankedQueue
  /** Palier, de `IRON` à `CHALLENGER` */
  tier: string
  /** Division, de `IV` à `I`. Toujours `I` à partir de `MASTER` : ne pas l'afficher pour ces paliers */
  rank: string
  leaguePoints: number
  wins: number
  losses: number
  /** Au moins 3 victoires d'affilée */
  hotStreak: boolean
}

/** Rang à un instant donné : un point du graphique d'évolution (miroir de RankPointResponse). */
export interface RankPoint {
  /** Date ISO 8601 où Riot a renvoyé ce rang */
  recordedAt: string
  tier: string
  rank: string
  leaguePoints: number
  wins: number
  losses: number
  /** Rang converti en LP cumulés depuis Fer IV 0 LP (Maître 0 LP = 2 800) : l'axe vertical */
  score: number
}

/**
 * Évolution du rang dans une file, depuis que RiftMind suit le joueur (miroir de RankHistoryResponse). Riot ne
 * fournit ni le MMR ni les rangs passés : ce sont les rangs vus à chaque recherche ou mise à jour.
 */
export interface RankHistory {
  queueType: RankedQueue
  /** Du plus ancien au plus récent, un point par changement */
  points: RankPoint[]
  /** Meilleur rang enregistré (le premier atteint en cas d'égalité) */
  peak: RankPoint
}

/** Joueur renvoyé par la recherche par Riot ID et par la mise à jour. */
export interface Player {
  /** Identifiant stable, pour les appels suivants (parties, stats…) */
  puuid: string
  /** Nom du Riot ID, avec l'écriture exacte de Riot */
  gameName: string
  /** Tag du Riot ID, sans le `#` */
  tagLine: string
  /** Identifiant de la plateforme en minuscules, ex. `euw1` */
  platform: string
  summonerLevel: number
  /** Icône Data Dragon : `img/profileicon/{id}.png` */
  profileIconId: number
  /** Date ISO 8601 de la dernière mise à jour depuis Riot */
  lastRefreshedAt: string
  /** SoloQ puis Flex, uniquement pour les files où le joueur est classé (liste vide sinon) */
  ranks: LeagueEntry[]
}
