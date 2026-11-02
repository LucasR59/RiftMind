import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'

import PlayerView from '@/views/PlayerView.vue'
import { ApiError } from '@/api/http'
import { getChampionStats, getPlayer, getPlayerSummary } from '@/api/players'
import { getPlayerMatches } from '@/api/matches'
import { reloadRecentSearches, useRecentSearches } from '@/composables/useRecentSearches'
import { createAppRouter } from '@/router'
import { matchPage, player, playerSummary } from '../../fixtures/api'

// Fonctions simulées ; les constantes (REFRESH_COOLDOWN_SECONDS) gardent leur valeur
vi.mock('@/api/players')
vi.mock('@/api/matches')

async function mountAt(path: string) {
  const router = createAppRouter(createMemoryHistory())
  await router.push(path)
  const wrapper = mount(PlayerView, { global: { plugins: [router, createPinia()] } })
  await flushPromises()
  return { router, wrapper }
}

beforeEach(() => {
  vi.mocked(getPlayerMatches).mockResolvedValue({ ...matchPage, hasMore: false })
  vi.mocked(getPlayerSummary).mockResolvedValue(playerSummary)
  localStorage.clear()
  reloadRecentSearches()
})

describe('PlayerView', () => {
  it('charge le joueur de l’URL et affiche son en-tête', async () => {
    vi.mocked(getPlayer).mockResolvedValue(player)

    const { wrapper } = await mountAt('/euw1/Joueur%20Test-EUW')

    expect(getPlayer).toHaveBeenCalledWith('euw1', 'Joueur Test', 'EUW')
    expect(wrapper.find('[data-testid="player-riot-id"]').text()).toBe('Joueur Test#EUW')
    expect(document.title).toBe('Joueur Test#EUW · RiftMind')
  })

  it("retient le joueur trouvé dans les recherches récentes, avec l'écriture exacte de Riot", async () => {
    vi.mocked(getPlayer).mockResolvedValue(player)

    await mountAt('/euw1/joueur%20test-euw')

    expect(useRecentSearches().items.value).toEqual([
      { platform: 'euw1', gameName: 'Joueur Test', tagLine: 'EUW' },
    ])
  })

  it("remplace l'URL par l'écriture exacte du Riot ID, sans recharger le joueur", async () => {
    vi.mocked(getPlayer).mockResolvedValue(player)

    const { router } = await mountAt('/euw1/joueur%20test-euw')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/euw1/Joueur%20Test-EUW')
    expect(getPlayer).toHaveBeenCalledTimes(1)
  })

  it('affiche « Joueur introuvable » avec le message du backend et la recherche', async () => {
    vi.mocked(getPlayer).mockRejectedValueOnce(
      new ApiError(404, 'Aucun joueur « Inconnu#EUW » sur EUW (Europe Ouest).', {
        status: 404,
        code: 'NOT_FOUND',
        detail: 'Aucun joueur « Inconnu#EUW » sur EUW (Europe Ouest).',
      }),
    )

    const { wrapper } = await mountAt('/euw1/Inconnu-EUW')

    const error = wrapper.find('[data-testid="error-state"]')
    expect(error.find('.error-state__title').text()).toBe('Joueur introuvable')
    expect(error.text()).toContain('Aucun joueur « Inconnu#EUW » sur EUW (Europe Ouest).')
    // Pas de « Réessayer » (le même Riot ID donnerait la même réponse) mais la recherche pour corriger
    expect(error.find('button.error-state__retry').exists()).toBe(false)
    expect(error.find('form[role="search"]').exists()).toBe(true)
    expect(useRecentSearches().items.value).toEqual([])
  })

  it('permet de réessayer quand les serveurs Riot sont indisponibles', async () => {
    vi.mocked(getPlayer).mockRejectedValueOnce(
      new ApiError(503, 'indisponible', { status: 503, code: 'RIOT_UNAVAILABLE' }),
    )
    const { wrapper } = await mountAt('/euw1/Joueur%20Test-EUW')

    const error = wrapper.find('[data-testid="error-state"]')
    expect(error.find('.error-state__title').text()).toBe('Serveurs Riot indisponibles')
    expect(error.find('form[role="search"]').exists()).toBe(false)

    vi.mocked(getPlayer).mockResolvedValue(player)
    await error.find('button.error-state__retry').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="player-riot-id"]').exists()).toBe(true)
  })

  it('affiche les parties, et les stats par champion à la première ouverture de leur onglet', async () => {
    vi.mocked(getPlayer).mockResolvedValue(player)
    vi.mocked(getChampionStats).mockResolvedValue([])
    const { wrapper } = await mountAt('/euw1/Joueur%20Test-EUW')

    expect(wrapper.find('[data-testid="tab-matches"]').attributes('aria-selected')).toBe('true')
    expect(getPlayerMatches).toHaveBeenCalledTimes(1)
    expect(getChampionStats).not.toHaveBeenCalled()

    await wrapper.find('[data-testid="tab-champions"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="tab-champions"]').attributes('aria-selected')).toBe('true')
    expect(wrapper.find('#panel-champions').exists()).toBe(true)
    expect(getChampionStats).toHaveBeenCalledWith(player.puuid, undefined)
  })

  it('recharge quand on passe à un autre joueur', async () => {
    vi.mocked(getPlayer).mockResolvedValue(player)
    const { router } = await mountAt('/euw1/Joueur%20Test-EUW')

    vi.mocked(getPlayer).mockResolvedValue({ ...player, gameName: 'Autre', puuid: 'autre' })
    await router.push('/euw1/Autre-EUW')
    await flushPromises()

    expect(getPlayer).toHaveBeenLastCalledWith('euw1', 'Autre', 'EUW')
  })
})
