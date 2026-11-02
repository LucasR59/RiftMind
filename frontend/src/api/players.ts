import { apiGet, apiPost, query, segment } from './http'
import type { Player } from '@/types/player'
import type { ChampionStats, PlayerSummary } from '@/types/stats'

/**
 * Joueur par Riot ID (`gameName#tagLine`) sur une plateforme (`euw1`, ou l'abréviation `EUW`).
 * Le backend le cherche en base, sinon chez Riot. Inconnu : `ApiError` 404 `NOT_FOUND`.
 */
export const getPlayer = (platform: string, gameName: string, tagLine: string) =>
  apiGet<Player>(`/players/${segment(platform)}/${segment(gameName)}/${segment(tagLine)}`)

/**
 * Met à jour le joueur depuis Riot. Trop tôt après la précédente : `ApiError` 429 `REFRESH_COOLDOWN`, avec
 * `retryAfterSeconds`.
 */
export const refreshPlayer = (puuid: string) =>
  apiPost<Player>(`/players/${segment(puuid)}/refresh`)

/** Statistiques par champion, hors remakes. `queue` absent : toutes les files. */
export const getChampionStats = (puuid: string, queue?: number) =>
  apiGet<ChampionStats[]>(`/players/${segment(puuid)}/champions${query({ queue })}`)

/** Résumé des 20 dernières parties, hors remakes. */
export const getPlayerSummary = (puuid: string) =>
  apiGet<PlayerSummary>(`/players/${segment(puuid)}/summary`)
