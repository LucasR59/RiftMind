import { describe, expect, it } from 'vitest'

import {
  formatCompact,
  formatCountdown,
  formatDuration,
  formatKdaRatio,
  formatNumber,
  formatPercent,
  formatRelativeTime,
  winrate,
} from '@/utils/format'

// Espaces insécables utilisés par Intl en français, remplacés pour lire les attentes plus facilement
const plain = (text: string) => text.replace(/[  ]/g, ' ')

describe('formatRelativeTime', () => {
  const now = Date.parse('2026-11-02T12:00:00Z')
  const ago = (seconds: number) => new Date(now - seconds * 1000).toISOString()

  it.each([
    [10, "à l'instant"],
    [5 * 60, 'il y a 5 minutes'],
    [60, 'il y a 1 minute'],
    [3 * 3600, 'il y a 3 heures'],
    [24 * 3600, 'hier'],
    [3 * 24 * 3600, 'il y a 3 jours'],
  ])('%i s → « %s »', (seconds, expected) => {
    expect(formatRelativeTime(ago(seconds), now)).toBe(expected)
  })

  it('affiche la date au-delà de 30 jours', () => {
    expect(plain(formatRelativeTime('2026-08-15T12:00:00Z', now))).toBe('15 août 2026')
  })

  it('renvoie une chaîne vide pour une date invalide', () => {
    expect(formatRelativeTime('pas une date', now)).toBe('')
  })
})

describe('durées et nombres', () => {
  it('formate la durée d’une partie', () => {
    expect(formatDuration(1463)).toBe('24:23')
    expect(formatDuration(65)).toBe('1:05')
    expect(formatDuration(3723)).toBe('1:02:03')
  })

  it('formate un compte à rebours', () => {
    expect(formatCountdown(45)).toBe('45 s')
    expect(formatCountdown(44.2)).toBe('45 s')
    expect(formatCountdown(65)).toBe('1:05')
    expect(formatCountdown(-3)).toBe('0 s')
  })

  it('formate les nombres à la française', () => {
    expect(plain(formatNumber(13852))).toBe('13 852')
    expect(formatNumber(3.456, 2)).toBe('3,46')
    expect(formatCompact(13852)).toBe('13,9 k')
    expect(formatCompact(950)).toBe('950')
    expect(plain(formatPercent(66.67))).toBe('67 %')
  })

  it('calcule le winrate et le ratio KDA', () => {
    expect(winrate(250, 200)).toBeCloseTo(55.56, 2)
    expect(winrate(0, 0)).toBe(0)
    expect(formatKdaRatio(6, 4, 3)).toBe('2,25')
    expect(formatKdaRatio(5, 0, 7)).toBe('Parfait')
  })
})
