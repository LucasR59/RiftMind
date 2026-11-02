import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import RankCards from '@/components/player/RankCards.vue'
import type { LeagueEntry } from '@/types/player'
import { player } from '../../fixtures/api'

const flex: LeagueEntry = {
  queueType: 'RANKED_FLEX_SR',
  tier: 'EMERALD',
  rank: 'IV',
  leaguePoints: 45,
  wins: 10,
  losses: 12,
  hotStreak: true,
}

const cards = (ranks: LeagueEntry[]) =>
  mount(RankCards, { props: { ranks } }).findAll('[data-testid="rank-card"]')

describe('RankCards', () => {
  it('affiche toujours SoloQ puis Flex', () => {
    const [solo, flexCard] = cards(player.ranks)

    expect(solo?.text()).toContain('Classée Solo/Duo')
    expect(flexCard?.text()).toContain('Classée Flexible')
  })

  it('affiche le rang, les LP, le bilan et le winrate', () => {
    const [solo] = cards(player.ranks)

    expect(solo?.find('[data-testid="rank-label"]').text()).toBe('Challenger')
    expect(solo?.text().replace(/\s/g, ' ')).toContain('1 234 LP')
    expect(solo?.text()).toContain('250V 200D')
    expect(solo?.find('[data-testid="rank-winrate"]').text().replace(/\s/g, ' ')).toBe('56 %')
    expect(solo?.find('[data-testid="rank-winrate"]').classes()).toContain(
      'rank-card__winrate--good',
    )
    expect(solo?.find('img').attributes('src')).toContain('ranked-mini-crests/challenger.svg')
  })

  it('affiche « Non classé » pour une file sans rang', () => {
    const [, flexCard] = cards(player.ranks)

    expect(flexCard?.text()).toContain('Non classé')
    expect(flexCard?.classes()).toContain('rank-card--unranked')
    expect(flexCard?.find('img').attributes('src')).toContain('ranked-mini-crests/unranked.svg')
  })

  it('un joueur non classé a deux cartes « Non classé », sans erreur', () => {
    const all = cards([])

    expect(all).toHaveLength(2)
    expect(all.every((card) => card.text().includes('Non classé'))).toBe(true)
  })

  it('affiche la division, un winrate sous 50 % et la série de victoires', () => {
    const [, flexCard] = cards([flex])

    expect(flexCard?.find('[data-testid="rank-label"]').text()).toBe('Émeraude IV')
    expect(flexCard?.find('[data-testid="rank-winrate"]').classes()).not.toContain(
      'rank-card__winrate--good',
    )
    expect(flexCard?.text()).toContain('Série de victoires')
  })
})
