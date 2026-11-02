import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'

import ApiErrorState from '@/components/ui/ApiErrorState.vue'
import { ApiError } from '@/api/http'
import type { ErrorCode } from '@/types/api'
import { describeError } from '@/utils/errors'

const apiError = (status: number, code?: ErrorCode, extra: Record<string, unknown> = {}) =>
  new ApiError(status, 'message technique', code ? { status, code, ...extra } : undefined)

describe('describeError', () => {
  it('joueur introuvable : message du backend, pas de nouvel essai', () => {
    const description = describeError(
      apiError(404, 'NOT_FOUND', { detail: 'Aucun joueur « X#Y » sur EUW (Europe Ouest).' }),
      'Joueur',
    )

    expect(description).toMatchObject({
      kind: 'not-found',
      title: 'Joueur introuvable',
      message: 'Aucun joueur « X#Y » sur EUW (Europe Ouest).',
      retryable: false,
    })
  })

  it('limite de requêtes Riot : nouvel essai après le délai du backend', () => {
    expect(
      describeError(apiError(503, 'RIOT_RATE_LIMITED', { retryAfterSeconds: 12 })),
    ).toMatchObject({
      kind: 'rate-limited',
      title: 'Trop de requêtes vers Riot',
      retryable: true,
      retryAfterSeconds: 12,
    })
  })

  it.each([
    [apiError(503, 'RIOT_UNAVAILABLE'), 'riot-unavailable'],
    [apiError(502, 'RIOT_ERROR'), 'riot-unavailable'],
    [apiError(500), 'riot-unavailable'],
    [apiError(400, 'INVALID_REQUEST', { detail: 'Plateforme inconnue' }), 'invalid'],
    [apiError(0), 'offline'],
    [apiError(418), 'unknown'],
    [new Error('bug'), 'unknown'],
  ])('%o → %s', (error, kind) => {
    expect(describeError(error).kind).toBe(kind)
  })
})

describe('ApiErrorState', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('bloque « Réessayer » pendant le délai de la limite de requêtes', async () => {
    const wrapper = mount(ApiErrorState, {
      props: { error: apiError(503, 'RIOT_RATE_LIMITED', { retryAfterSeconds: 3 }) },
    })
    const button = () => wrapper.find('button')

    expect(wrapper.text()).toContain('Trop de requêtes vers Riot')
    expect(button().text()).toBe('Réessayer dans 3 s')
    expect(button().attributes('disabled')).toBeDefined()

    await vi.advanceTimersByTimeAsync(3000)
    expect(button().text()).toBe('Réessayer')
    expect(button().attributes('disabled')).toBeUndefined()

    await button().trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })

  it('garde le titre du bloc, sauf pour une ressource introuvable', () => {
    const unavailable = mount(ApiErrorState, {
      props: { error: apiError(503, 'RIOT_UNAVAILABLE'), title: 'Historique indisponible' },
    })
    const notFound = mount(ApiErrorState, {
      props: { error: apiError(404, 'NOT_FOUND'), subject: 'Partie', title: 'Détail indisponible' },
    })

    expect(unavailable.find('.error-state__title').text()).toBe('Historique indisponible')
    expect(unavailable.text()).toContain('temporairement indisponibles')
    expect(notFound.find('.error-state__title').text()).toBe('Partie introuvable')
    expect(notFound.find('button').exists()).toBe(false)
  })

  it('transmet le type d’erreur au contenu ajouté', () => {
    const wrapper = mount(ApiErrorState, {
      props: { error: apiError(404, 'NOT_FOUND') },
      slots: { default: '<template #default="{ kind }"><em>{{ kind }}</em></template>' },
    })

    expect(wrapper.find('em').text()).toBe('not-found')
  })
})
