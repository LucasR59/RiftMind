import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'
import type { Pinia } from 'pinia'

import MatchCard from '@/components/match/MatchCard.vue'
import MatchDetail from '@/components/match/MatchDetail.vue'
import { getMatch } from '@/api/matches'
import { createAppRouter } from '@/router'
import type { MatchDetail as MatchDetailData, Participant } from '@/types/match'
import { kalista, MATCH_ID, matchDetail, matchSummary, PUUID } from '../../fixtures/api'
import { loadedStaticData } from '../../fixtures/ddragon'

vi.mock('@/api/matches')

let pinia: Pinia
const plugins = () => [pinia, createAppRouter(createMemoryHistory())]

/** Partie complète : le joueur (19 708 dégâts) et 9 autres, dont le plus gros dégâts (30 000) chez les rouges. */
function fullDetail(): MatchDetailData {
  const others: Participant[] = Array.from({ length: 9 }, (_, i) => ({
    ...kalista,
    participantId: i < 3 ? i + 1 : i + 2,
    puuid: `p${i}`,
    gameName: i === 8 ? null : `Joueur ${i}`,
    tagLine: i === 8 ? null : 'TEST',
    teamId: i < 4 ? 100 : 200,
    win: i < 4,
    damageChampions: i === 4 ? 30000 : 10000,
  }))
  return { ...matchDetail, participants: [kalista, ...others] }
}

beforeEach(async () => {
  pinia = (await loadedStaticData()).pinia
})

describe('MatchDetail', () => {
  const mountDetail = () =>
    mount(MatchDetail, {
      props: { matchId: MATCH_ID, platform: 'euw1', highlightPuuid: PUUID },
      global: { plugins: plugins() },
    })

  it('charge la partie et affiche les deux équipes de 5 joueurs', async () => {
    vi.mocked(getMatch).mockResolvedValue(fullDetail())

    const wrapper = mountDetail()
    await flushPromises()

    expect(getMatch).toHaveBeenCalledWith(MATCH_ID)
    const teams = wrapper.findAll('[data-testid="match-detail-team"]')
    expect(teams).toHaveLength(2)
    expect(teams[0]?.find('caption').text()).toContain('Victoire — Équipe bleue')
    expect(teams[0]?.find('caption').text()).toContain('28 / 17 / 35')
    expect(teams[1]?.find('caption').text()).toContain('Défaite — Équipe rouge')
    expect(teams[0]?.findAll('[data-testid="match-detail-player"]')).toHaveLength(5)
    expect(teams[1]?.findAll('[data-testid="match-detail-player"]')).toHaveLength(5)
  })

  it('met en évidence le joueur du profil et affiche ses stats', async () => {
    vi.mocked(getMatch).mockResolvedValue(fullDetail())

    const wrapper = mountDetail()
    await flushPromises()

    const me = wrapper.find('.match-detail__me')
    expect(me.text()).toContain('Joueur Test')
    expect(me.text()).toContain('6/4/3')
    expect(me.text().replace(/\s/g, ' ')).toContain('19 708')
    expect(me.find('a').attributes('href')).toBe('/euw1/Joueur%20Test-EUW')
    expect(me.findAll('img[alt="Kalista"]')).toHaveLength(1)
  })

  it('dessine les barres de dégâts par rapport au plus gros total de la partie', async () => {
    vi.mocked(getMatch).mockResolvedValue(fullDetail())

    const wrapper = mountDetail()
    await flushPromises()

    const bars = wrapper.findAll('[data-testid="damage-bar"]').map((b) => b.attributes('style'))
    // 19 708 / 30 000 = 66 %
    expect(bars[0]).toContain('width: 66%')
    expect(bars).toContainEqual(expect.stringContaining('width: 100%'))
  })

  it('affiche une erreur et permet de réessayer', async () => {
    vi.mocked(getMatch).mockRejectedValueOnce(new Error('réseau'))
    const wrapper = mountDetail()
    await flushPromises()

    expect(wrapper.find('[data-testid="error-state"]').exists()).toBe(true)

    vi.mocked(getMatch).mockResolvedValue(fullDetail())
    await wrapper.find('[data-testid="error-state"] button').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('[data-testid="match-detail-player"]')).toHaveLength(10)
  })
})

describe('MatchCard dépliable', () => {
  const mountCard = () =>
    mount(MatchCard, {
      props: { match: matchSummary, platform: 'euw1', now: Date.now() },
      global: { plugins: plugins() },
    })

  it('charge le détail à la première ouverture seulement', async () => {
    vi.mocked(getMatch).mockResolvedValue(fullDetail())
    const wrapper = mountCard()
    const toggle = wrapper.find('[data-testid="match-toggle"]')
    // v-show : le détail reste dans la page, masqué par display: none
    const hidden = () =>
      (wrapper.find('.match-card__detail').attributes('style') ?? '').includes('display: none')

    expect(toggle.attributes('aria-expanded')).toBe('false')
    expect(wrapper.find('[data-testid="match-detail"]').exists()).toBe(false)
    expect(getMatch).not.toHaveBeenCalled()

    await toggle.trigger('click')
    await flushPromises()
    expect(toggle.attributes('aria-expanded')).toBe('true')
    expect(hidden()).toBe(false)
    expect(wrapper.find('[data-testid="match-detail"]').exists()).toBe(true)
    expect(toggle.attributes('aria-controls')).toBe(
      wrapper.find('.match-card__detail').attributes('id'),
    )

    await toggle.trigger('click')
    expect(hidden()).toBe(true)
    await toggle.trigger('click')
    expect(getMatch).toHaveBeenCalledTimes(1)
  })
})
