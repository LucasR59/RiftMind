import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

import RankCards from '@/components/player/RankCards.vue'
import { ApiError } from '@/api/http'
import { getRankHistory } from '@/api/players'
import type { LeagueEntry } from '@/types/player'
import { player, PUUID, soloHistory } from '../../fixtures/api'

// Peak et évolution du rang sous chaque carte (tâche 7.10)

vi.mock('@/api/players')

const flex: LeagueEntry = {
  queueType: 'RANKED_FLEX_SR',
  tier: 'GOLD',
  rank: 'IV',
  leaguePoints: 10,
  wins: 3,
  losses: 1,
  hotStreak: false,
}

async function mountCards(refreshKey = 0) {
  const wrapper = mount(RankCards, {
    props: { ranks: [...player.ranks, flex], puuid: PUUID, refreshKey },
  })
  await flushPromises()
  const [solo, flexCard] = wrapper.findAll('[data-testid="rank-card"]')
  return { wrapper, solo, flexCard }
}

beforeEach(() => {
  vi.mocked(getRankHistory).mockReset()
})

describe('RankCards : évolution du rang', () => {
  it('affiche le peak et la courbe sous le rang', async () => {
    vi.mocked(getRankHistory).mockResolvedValue([soloHistory])

    const { solo } = await mountCards()

    expect(getRankHistory).toHaveBeenCalledWith(PUUID)
    expect(solo?.find('[data-testid="rank-peak"] .rank-card__peak-value').text()).toBe(
      'Platine IV · 12 LP',
    )
    expect(solo?.find('[data-testid="rank-history-chart"]').exists()).toBe(true)
  })

  it('chaque file a sa propre évolution', async () => {
    vi.mocked(getRankHistory).mockResolvedValue([soloHistory])

    const { flexCard } = await mountCards()

    expect(flexCard?.find('[data-testid="rank-history"]').exists()).toBe(false)
  })

  it('avec un seul rang enregistré, affiche le peak et explique que la courbe viendra', async () => {
    const first = soloHistory.points[0]!
    vi.mocked(getRankHistory).mockResolvedValue([
      { queueType: 'RANKED_FLEX_SR', points: [{ ...first }], peak: { ...first } },
    ])

    const { flexCard } = await mountCards()

    expect(flexCard?.find('[data-testid="rank-peak"] .rank-card__peak-value').text()).toBe(
      'Or II · 45 LP',
    )
    expect(flexCard?.find('[data-testid="rank-history-chart"]').exists()).toBe(false)
    expect(flexCard?.find('[data-testid="rank-history-hint"]').text()).toContain(
      'Suivi depuis le 1 oct.',
    )
  })

  it("garde les cartes de rang si l'évolution ne se charge pas", async () => {
    vi.mocked(getRankHistory).mockRejectedValue(new ApiError(0, 'API injoignable'))

    const { solo } = await mountCards()

    expect(solo?.find('[data-testid="rank-label"]').text()).toBe('Challenger')
    expect(solo?.text()).toContain('Évolution du rang indisponible.')
  })

  it('recharge l’évolution après « Mettre à jour »', async () => {
    vi.mocked(getRankHistory).mockResolvedValue([soloHistory])
    const { wrapper } = await mountCards()

    await wrapper.setProps({ refreshKey: 1 })
    await flushPromises()

    expect(getRankHistory).toHaveBeenCalledTimes(2)
  })

  it("sans PUUID, n'appelle pas l'API", async () => {
    mount(RankCards, { props: { ranks: player.ranks } })
    await flushPromises()

    expect(getRankHistory).not.toHaveBeenCalled()
  })
})
