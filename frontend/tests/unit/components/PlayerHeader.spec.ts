import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'

import PlayerHeader from '@/components/player/PlayerHeader.vue'
import { ApiError } from '@/api/http'
import { refreshPlayer } from '@/api/players'
import type { Player } from '@/types/player'
import { player } from '../../fixtures/api'

// Fonctions simulées ; les constantes (REFRESH_COOLDOWN_SECONDS) gardent leur valeur
vi.mock('@/api/players')

const LAST_REFRESH = Date.parse(player.lastRefreshedAt)

function mountHeader(overrides: Partial<Player> = {}) {
  return mount(PlayerHeader, {
    props: { player: { ...player, ...overrides } },
    global: { plugins: [createPinia()] },
  })
}

const button = (wrapper: ReturnType<typeof mountHeader>) =>
  wrapper.find('[data-testid="refresh-button"]')

describe('PlayerHeader', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('affiche le Riot ID, le niveau, la région et la date de mise à jour', () => {
    vi.setSystemTime(LAST_REFRESH + 3 * 60 * 60 * 1000)

    const wrapper = mountHeader()

    expect(wrapper.find('[data-testid="player-riot-id"]').text()).toBe('Joueur Test#EUW')
    expect(wrapper.find('[data-testid="player-level"]').text()).toBe('512')
    expect(wrapper.text()).toContain('EUW')
    expect(wrapper.find('[data-testid="player-updated"]').text()).toBe('Mis à jour il y a 3 heures')
    expect(button(wrapper).text()).toBe('Mettre à jour')
    expect(button(wrapper).attributes('disabled')).toBeUndefined()
  })

  it('désactive le bouton avec un compte à rebours juste après une mise à jour', async () => {
    vi.setSystemTime(LAST_REFRESH + 30_000)
    const wrapper = mountHeader()

    expect(button(wrapper).text()).toBe('Mettre à jour (1:30)')
    expect(button(wrapper).attributes('disabled')).toBeDefined()

    await vi.advanceTimersByTimeAsync(45_000)
    expect(button(wrapper).text()).toBe('Mettre à jour (45 s)')

    await vi.advanceTimersByTimeAsync(45_000)
    expect(button(wrapper).text()).toBe('Mettre à jour')
    expect(button(wrapper).attributes('disabled')).toBeUndefined()
  })

  it('émet le joueur à jour après une mise à jour réussie', async () => {
    vi.setSystemTime(LAST_REFRESH + 10 * 60_000)
    const updated = { ...player, summonerLevel: 513, lastRefreshedAt: new Date().toISOString() }
    let resolve: (value: Player) => void = () => {}
    vi.mocked(refreshPlayer).mockReturnValue(new Promise((r) => (resolve = r)))
    const wrapper = mountHeader()

    await button(wrapper).trigger('click')
    expect(button(wrapper).text()).toBe('Mise à jour…')
    expect(button(wrapper).attributes('disabled')).toBeDefined()
    resolve(updated)
    await flushPromises()

    expect(refreshPlayer).toHaveBeenCalledWith(player.puuid)
    expect(wrapper.emitted('refreshed')?.[0]).toEqual([updated])
  })

  it('suit le délai renvoyé par le backend (429 REFRESH_COOLDOWN)', async () => {
    vi.setSystemTime(LAST_REFRESH + 10 * 60_000)
    vi.mocked(refreshPlayer).mockRejectedValue(
      new ApiError(429, 'trop tôt', {
        status: 429,
        code: 'REFRESH_COOLDOWN',
        retryAfterSeconds: 42,
      }),
    )
    const wrapper = mountHeader()

    await button(wrapper).trigger('click')
    await flushPromises()

    expect(button(wrapper).text()).toBe('Mettre à jour (42 s)')
    expect(wrapper.find('[data-testid="refresh-error"]').exists()).toBe(false)
    expect(wrapper.emitted('refreshed')).toBeUndefined()
  })

  it('affiche une erreur si Riot ne répond pas, et laisse réessayer', async () => {
    vi.setSystemTime(LAST_REFRESH + 10 * 60_000)
    vi.mocked(refreshPlayer).mockRejectedValue(
      new ApiError(503, 'indisponible', { status: 503, code: 'RIOT_UNAVAILABLE' }),
    )
    const wrapper = mountHeader()

    await button(wrapper).trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="refresh-error"]').text()).toContain(
      'La mise à jour a échoué. Les données de League of Legends sont temporairement indisponibles.',
    )
    expect(button(wrapper).attributes('disabled')).toBeUndefined()
  })
})
