// Axe vertical du graphique d'évolution du rang : le score renvoyé par l'API (LP cumulés depuis Fer IV 0 LP, voir
// RankScore côté backend) et ses graduations, posées sur les changements de division ou de palier.

import { formatRank, tierLabel } from '@/constants/tiers'
import { formatNumber } from './format'

/** Paliers à divisions, du plus bas au plus haut : 400 points chacun, 100 par division. */
const DIVIDED_TIERS = ['IRON', 'BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'EMERALD', 'DIAMOND']
const DIVISIONS = ['IV', 'III', 'II', 'I']
export const TIER_SIZE = 400
export const DIVISION_SIZE = 100
/** Score de Maître 0 LP. Au-dessus, seuls les LP comptent (Grand Maître et Challenger sont des seuils de LP). */
export const APEX_BASE = DIVIDED_TIERS.length * TIER_SIZE

/** Pas possibles entre deux graduations : division, deux divisions, palier, puis plus large. */
const STEPS = [
  DIVISION_SIZE,
  2 * DIVISION_SIZE,
  TIER_SIZE,
  2 * TIER_SIZE,
  3 * TIER_SIZE,
  5 * TIER_SIZE,
]

export interface ScoreScale {
  /** Bas de l'axe (une graduation) */
  min: number
  /** Haut de l'axe (une graduation) */
  max: number
  /** Écart entre deux graduations */
  step: number
  /** Graduations, de bas en haut */
  ticks: number[]
}

/**
 * Axe qui contient tous les scores, borné et gradué sur des multiples du plus petit pas possible qui donne au plus
 * `maxTicks` graduations : on voit les divisions si le joueur a peu bougé, les paliers sinon.
 */
export function scoreScale(scores: number[], maxTicks = 5): ScoreScale {
  const lowest = Math.min(...scores)
  const highest = Math.max(...scores)
  let scale: ScoreScale | null = null
  for (const step of STEPS) {
    const min = Math.floor(lowest / step) * step
    let max = Math.ceil(highest / step) * step
    if (max === min) max = min + step
    const ticks = Array.from({ length: (max - min) / step + 1 }, (_, i) => min + i * step)
    scale = { min, max, step, ticks }
    if (ticks.length <= maxTicks) break
  }
  return scale as ScoreScale
}

/**
 * Libellé d'une graduation : « Or II » (pas d'une division), « Or » (pas d'un palier ou plus), « Maître » à
 * 2 800, puis les LP au-dessus, ex. « 400 LP ».
 */
export function scoreLabel(score: number, step: number): string {
  if (score >= APEX_BASE) {
    return score === APEX_BASE ? tierLabel('MASTER') : `${formatNumber(score - APEX_BASE)} LP`
  }
  const tier = DIVIDED_TIERS[Math.floor(score / TIER_SIZE)] ?? 'IRON'
  if (step >= TIER_SIZE) return tierLabel(tier)
  return formatRank(tier, DIVISIONS[Math.floor((score % TIER_SIZE) / DIVISION_SIZE)])
}

/** Rang complet, ex. « Or II · 45 LP », « Challenger · 1 234 LP ». */
export function formatRankWithLp(tier: string, rank: string, leaguePoints: number): string {
  return `${formatRank(tier, rank)} · ${formatNumber(leaguePoints)} LP`
}

/** Variation de LP signée, ex. « +18 LP », « −15 LP » (vrai signe moins), « ±0 LP ». */
export function formatLpChange(delta: number): string {
  if (delta === 0) return '±0 LP'
  return `${delta > 0 ? '+' : '−'}${formatNumber(Math.abs(delta))} LP`
}

/** Sens d'une variation, pour sa couleur (le signe du texte porte déjà le sens). */
export function lpDirection(delta: number): 'up' | 'down' | 'flat' {
  if (delta > 0) return 'up'
  return delta < 0 ? 'down' : 'flat'
}

/** « 1 partie », « 3 parties ». */
export function formatGames(count: number): string {
  return `${formatNumber(count)} partie${count > 1 ? 's' : ''}`
}

/** Ce qu'il faut d'un point pour compter les parties : son bilan dans la file. */
interface SeasonRecord {
  wins: number
  losses: number
  score: number
}

/**
 * Parties jouées depuis le premier point, pour chaque point : l'axe horizontal du graphique « par partie ». Le bilan
 * (victoires + défaites) donne le nombre de parties de la saison ; s'il baisse, c'est une nouvelle saison qui repart
 * de zéro, et ses parties s'ajoutent aux précédentes.
 */
export function gamesPlayed(points: readonly SeasonRecord[]): number[] {
  let total = 0
  return points.map((point, i) => {
    const previous = points[i - 1]
    if (previous) {
      const before = previous.wins + previous.losses
      const now = point.wins + point.losses
      total += now >= before ? now - before : now
    }
    return total
  })
}

/** Variation depuis le point précédent : LP (différence de score) et parties jouées. `null` pour le premier point. */
export interface PointChange {
  lp: number
  games: number
}

export function pointChanges(points: readonly SeasonRecord[]): (PointChange | null)[] {
  const games = gamesPlayed(points)
  return points.map((point, i) => {
    const previous = points[i - 1]
    if (!previous) return null
    return { lp: point.score - previous.score, games: (games[i] ?? 0) - (games[i - 1] ?? 0) }
  })
}

/** « +31 LP en 3 parties », « −15 LP en 1 partie », « −50 LP sans partie », « Début du suivi ». */
export function formatPointChange(change: PointChange | null): string {
  if (!change) return 'Début du suivi'
  const games = change.games > 0 ? `en ${formatGames(change.games)}` : 'sans partie'
  return `${formatLpChange(change.lp)} ${games}`
}
