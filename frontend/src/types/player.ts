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
