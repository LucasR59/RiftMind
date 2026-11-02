import { apiGet, query, segment } from './http'
import type { MatchDetail, MatchPage } from '@/types/match'

export interface MatchHistoryOptions {
  /** Numéro de page, à partir de 0 (défaut côté backend : 0) */
  page?: number
  /** Parties par page, de 1 à 50 (défaut côté backend : 10) */
  size?: number
  /** File de jeu, ex. 420 = SoloQ ; absent : toutes */
  queue?: number
}

/** Historique d'un joueur, de la partie la plus récente à la plus ancienne. */
export const getPlayerMatches = (puuid: string, options: MatchHistoryOptions = {}) =>
  apiGet<MatchPage>(`/players/${segment(puuid)}/matches${query({ ...options })}`)

/** Détail complet d'une partie. Inconnue ou personnalisée : `ApiError` 404. */
export const getMatch = (matchId: string) => apiGet<MatchDetail>(`/matches/${segment(matchId)}`)
