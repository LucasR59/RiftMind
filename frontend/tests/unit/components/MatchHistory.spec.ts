import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'
import type { Pinia } from 'pinia'

import MatchCard from '@/components/match/MatchCard.vue'
import MatchHistory from '@/components/match/MatchHistory.vue'
import { getPlayerMatches } from '@/api/matches'
import { createAppRouter } from '@/router'
import type { CompactParticipant, MatchSummary } from '@/types/match'
import { compactKalista, matchSummary, PUUID } from '../../fixtures/api'
import { loadedStaticData } from '../../fixtures/ddragon'

vi.mock('@/api/matches')

let pinia: Pinia
const plugins = () => [pinia, createAppRouter(createMemoryHistory())]

const NOW = Date.parse('2026-11-01T22:00:00Z')

/** Une partie avec 10 joueurs : le joueur (équipe bleue), 4 coéquipiers, 5 adversaires. */
function fullMatch(overrides: Partial<MatchSummary> = {}): MatchSummary {
  const others: CompactParticipant[] = Array.from({ length: 9 }, (_, i) => ({
    puuid: `p${i + 2}`,
    gameName: i === 8 ? null : `Joueur ${i + 2}`,
    tagLine: i === 8 ? null : 'TEST',
    teamId: i < 4 ? 100 : 200,
    championId: 62,
    championName: 'MonkeyKing',
  }))
  return { ...matchSummary, participants: [compactKalista, ...others], ...overrides }
}

const page = (items: MatchSummary[], pageNumber: number, hasMore: boolean) => ({
  items,
  page: pageNumber,
  size: 10,
  hasMore,
})

beforeEach(async () => {
  pinia = (await loadedStaticData()).pinia
})

describe('MatchCard', () => {
  const mountCard = (match: MatchSummary) =>
    mount(MatchCard, {
      props: { match, platform: 'euw1', now: NOW },
      global: { plugins: plugins() },
    })

  it('affiche la file, la date, le résultat, la durée et les stats du joueur', () => {
    const wrapper = mountCard(fullMatch())

    expect(wrapper.classes()).toContain('match-card--win')
    expect(wrapper.text()).toContain('SoloQ')
    expect(wrapper.text()).toContain('il y a 2 heures')
    expect(wrapper.find('[data-testid="match-result"]').text()).toBe('Victoire')
    expect(wrapper.text()).toContain('24:23')
    expect(wrapper.find('[data-testid="match-kda"]').text().replace(/\s+/g, ' ')).toBe('6 / 4 / 3')
    expect(wrapper.text()).toContain('2,25 KDA')
    // 250 CS en 24 min 23 s
    expect(wrapper.find('[data-testid="match-cs"]').text()).toBe('250 CS (10,3/min)')
    expect(wrapper.find('[data-testid="match-items"]').findAll('img')).toHaveLength(7)
    expect(wrapper.find('img[alt="Kalista"]').exists()).toBe(true)
  })

  it('distingue défaite et remake', () => {
    const loss = mountCard(fullMatch({ player: { ...matchSummary.player, win: false } }))
    const remake = mountCard(fullMatch({ remake: true }))

    expect(loss.classes()).toContain('match-card--loss')
    expect(loss.find('[data-testid="match-result"]').text()).toBe('Défaite')
    expect(remake.classes()).toContain('match-card--remake')
    expect(remake.find('[data-testid="match-result"]').text()).toBe('Remake')
  })

  it('liste les deux équipes avec des liens vers les profils', () => {
    const wrapper = mountCard(fullMatch())

    const teams = wrapper.findAll('.match-card__team')
    expect(teams).toHaveLength(2)
    expect(teams[0]?.findAll('li')).toHaveLength(5)
    expect(teams[1]?.findAll('li')).toHaveLength(5)
    expect(teams[0]?.find('.match-card__player--me').text()).toContain('Joueur Test')
    expect(teams[0]?.find('a').attributes('href')).toBe('/euw1/Joueur%20Test-EUW')
    // Joueur sans Riot ID connu (bot, ancienne partie) : pas de lien
    expect(teams[1]?.text()).toContain('Inconnu')
  })

  it('affiche une case vide pour un emplacement d’objet vide', () => {
    const items = [1086, 0, 0, 0, 0, 0, 3363]
    const wrapper = mountCard(fullMatch({ player: { ...matchSummary.player, items } }))

    const slots = wrapper.find('[data-testid="match-items"]')
    expect(slots.findAll('img')).toHaveLength(2)
    expect(slots.findAll('[data-testid="game-image-empty"]')).toHaveLength(5)
  })
})

describe('MatchHistory', () => {
  const mountHistory = (refreshKey = 0) =>
    mount(MatchHistory, {
      props: { puuid: PUUID, platform: 'euw1', refreshKey },
      global: { plugins: plugins() },
    })

  const match = (n: number) => fullMatch({ matchId: `EUW1_${n}` })

  it('charge la première page de 10 parties', async () => {
    vi.mocked(getPlayerMatches).mockResolvedValue(page([match(1), match(2)], 0, false))

    const wrapper = mountHistory()
    await flushPromises()

    expect(getPlayerMatches).toHaveBeenCalledWith(PUUID, { page: 0, size: 10 })
    expect(wrapper.findAll('[data-testid="match-card"]')).toHaveLength(2)
    expect(wrapper.find('[data-testid="load-more"]').exists()).toBe(false)
  })

  it('« Voir plus » ajoute la page suivante à la suite', async () => {
    vi.mocked(getPlayerMatches)
      .mockResolvedValueOnce(page([match(1), match(2)], 0, true))
      .mockResolvedValueOnce(page([match(3)], 1, false))
    const wrapper = mountHistory()
    await flushPromises()

    await wrapper.find('[data-testid="load-more"]').trigger('click')
    await flushPromises()

    expect(getPlayerMatches).toHaveBeenLastCalledWith(PUUID, { page: 1, size: 10 })
    expect(wrapper.findAll('[data-testid="match-card"]')).toHaveLength(3)
    expect(wrapper.find('[data-testid="load-more"]').exists()).toBe(false)
  })

  it('recharge depuis la première page après une mise à jour du joueur', async () => {
    vi.mocked(getPlayerMatches).mockResolvedValue(page([match(1)], 0, false))
    const wrapper = mountHistory(0)
    await flushPromises()

    vi.mocked(getPlayerMatches).mockResolvedValue(page([match(9), match(1)], 0, false))
    await wrapper.setProps({ refreshKey: 1 })
    await flushPromises()

    expect(getPlayerMatches).toHaveBeenCalledTimes(2)
    expect(wrapper.findAll('[data-testid="match-card"]')).toHaveLength(2)
  })

  it('invite à mettre à jour un joueur sans partie', async () => {
    vi.mocked(getPlayerMatches).mockResolvedValue(page([], 0, false))

    const wrapper = mountHistory()
    await flushPromises()

    expect(wrapper.find('[data-testid="history-empty"]').text()).toContain('Mettre à jour')
  })

  it('affiche une erreur et permet de réessayer', async () => {
    vi.mocked(getPlayerMatches).mockRejectedValueOnce(new Error('réseau'))
    const wrapper = mountHistory()
    await flushPromises()

    expect(wrapper.find('[data-testid="error-state"]').exists()).toBe(true)

    vi.mocked(getPlayerMatches).mockResolvedValue(page([match(1)], 0, false))
    await wrapper.find('[data-testid="error-state"] button').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('[data-testid="match-card"]')).toHaveLength(1)
  })
})
