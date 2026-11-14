import { describe, expect, it } from 'vitest'

import {
  APEX_BASE,
  formatLpChange,
  formatPointChange,
  formatRankWithLp,
  gamesPlayed,
  pointChanges,
  scoreLabel,
  scoreScale,
} from '@/utils/rankScale'

// Scores : LP cumulés depuis Fer IV 0 LP (Or IV 0 LP = 1 200, Maître 0 LP = 2 800), comme RankScore côté backend.

describe('scoreScale', () => {
  it('gradue par division quand le joueur a peu bougé', () => {
    // Or II 45 LP → Or I 10 LP
    expect(scoreScale([1445, 1510])).toEqual({
      min: 1400,
      max: 1600,
      step: 100,
      ticks: [1400, 1500, 1600],
    })
  })

  it('élargit le pas pour garder au plus 5 graduations', () => {
    // Argent IV 0 LP → Émeraude IV 30 LP : 2 paliers et demi
    const scale = scoreScale([800, 2030])

    expect(scale.step).toBe(400)
    expect(scale.ticks).toEqual([800, 1200, 1600, 2000, 2400])
    expect(scale.min).toBeLessThanOrEqual(800)
    expect(scale.max).toBeGreaterThanOrEqual(2030)
  })

  it('donne toujours un axe de hauteur non nulle, même pour des scores égaux ou sur une graduation', () => {
    expect(scoreScale([1200, 1200])).toEqual({
      min: 1200,
      max: 1300,
      step: 100,
      ticks: [1200, 1300],
    })
  })

  it('couvre aussi les paliers apex', () => {
    const scale = scoreScale([APEX_BASE + 1100, APEX_BASE + 1234])

    expect(scale.min).toBeLessThanOrEqual(APEX_BASE + 1100)
    expect(scale.max).toBeGreaterThanOrEqual(APEX_BASE + 1234)
    expect(scale.ticks.length).toBeLessThanOrEqual(5)
  })
})

describe('scoreLabel', () => {
  it('nomme la division avec un pas d’une division', () => {
    expect(scoreLabel(1400, 100)).toBe('Or II')
    expect(scoreLabel(1200, 100)).toBe('Or IV')
    expect(scoreLabel(2700, 100)).toBe('Diamant I')
  })

  it('nomme seulement le palier avec un pas plus large', () => {
    expect(scoreLabel(1600, 400)).toBe('Platine')
    expect(scoreLabel(2000, 800)).toBe('Émeraude')
  })

  it('affiche Maître puis les LP au-dessus', () => {
    expect(scoreLabel(APEX_BASE, 100)).toBe('Maître')
    expect(scoreLabel(APEX_BASE + 400, 400)).toBe('400 LP')
    expect(scoreLabel(APEX_BASE + 1200, 400)).toBe('1 200 LP')
  })
})

describe('formatRankWithLp', () => {
  it('assemble le rang et les LP, sans division pour les paliers apex', () => {
    expect(formatRankWithLp('GOLD', 'II', 45)).toBe('Or II · 45 LP')
    expect(formatRankWithLp('CHALLENGER', 'I', 1234)).toBe('Challenger · 1 234 LP')
  })
})

const at = (wins: number, losses: number, score = 0) => ({ wins, losses, score })

describe('gamesPlayed', () => {
  it('compte les parties jouées depuis le premier point, d’après le bilan', () => {
    expect(gamesPlayed([at(10, 8), at(11, 8), at(11, 8), at(14, 10)])).toEqual([0, 1, 1, 6])
  })

  it('ajoute les parties de la nouvelle saison quand le bilan repart de zéro', () => {
    expect(gamesPlayed([at(120, 100), at(121, 100), at(1, 0), at(3, 2)])).toEqual([0, 1, 2, 6])
  })
})

describe('pointChanges', () => {
  it('donne la variation de LP et le nombre de parties depuis le point précédent', () => {
    expect(pointChanges([at(10, 8, 1445), at(11, 8, 1463), at(11, 10, 1430)])).toEqual([
      null,
      { lp: 18, games: 1 },
      { lp: -33, games: 2 },
    ])
  })
})

describe('formatLpChange et formatPointChange', () => {
  it('signe la variation, avec un vrai signe moins', () => {
    expect(formatLpChange(18)).toBe('+18 LP')
    expect(formatLpChange(-15)).toBe('−15 LP')
    expect(formatLpChange(0)).toBe('±0 LP')
  })

  it('précise le nombre de parties, ou leur absence', () => {
    expect(formatPointChange({ lp: 18, games: 1 })).toBe('+18 LP en 1 partie')
    expect(formatPointChange({ lp: 31, games: 3 })).toBe('+31 LP en 3 parties')
    expect(formatPointChange({ lp: -50, games: 0 })).toBe('−50 LP sans partie')
    expect(formatPointChange(null)).toBe('Début du suivi')
  })
})
