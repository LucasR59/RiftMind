// Statistiques (miroir de ChampionStatsResponse et PlayerSummaryResponse). Valeurs non arrondies.

import type { Position } from './match'

/** Statistiques d'un joueur sur un champion, hors remakes. */
export interface ChampionStats {
  championId: number
  championName: string
  games: number
  wins: number
  losses: number
  /** Pourcentage de victoires, de 0 à 100 */
  winrate: number
  avgKills: number
  avgDeaths: number
  avgAssists: number
  /** (K + A) / D sur l'ensemble des parties ; K + A sans aucune mort */
  kda: number
  csPerMinute: number
  avgDamage: number
}

/** Nombre de parties jouées à un rôle. */
export interface RoleShare {
  position: Exclude<Position, ''>
  games: number
}

/** Résumé des 20 dernières parties d'un joueur, hors remakes. */
export interface PlayerSummary {
  /** Parties prises en compte (au plus 20) */
  games: number
  wins: number
  losses: number
  winrate: number
  avgKills: number
  avgDeaths: number
  avgAssists: number
  kda: number
  /** Du plus joué au moins joué, sans les parties sans rôle */
  roles: RoleShare[]
}
