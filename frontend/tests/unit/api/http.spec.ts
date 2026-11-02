import { describe, it, expect, vi } from 'vitest'

import { apiGet, apiPost, ApiError, query, segment } from '@/api/http'
import { jsonResponse } from '../../fixtures/api'

describe('apiGet', () => {
  it('préfixe le chemin avec /api et renvoie le JSON', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify({ status: 'ok' }), { status: 200 }))

    const result = await apiGet<{ status: string }>('/ping')

    expect(result).toEqual({ status: 'ok' })
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/ping',
      expect.objectContaining({ method: 'GET', headers: { Accept: 'application/json' } }),
    )
  })

  it("lève une ApiError avec le code HTTP en cas d'échec", async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 503 }))

    await expect(apiGet('/ping')).rejects.toMatchObject({ name: 'ApiError', status: 503 })
    await expect(apiGet('/ping')).rejects.toBeInstanceOf(ApiError)
  })

  it("lit l'erreur RFC 9457 du backend : code, message et délai", async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(
        {
          title: 'Mise à jour trop récente',
          detail: 'Ce joueur vient d’être mis à jour. Nouvelle mise à jour possible dans 90 s.',
          code: 'REFRESH_COOLDOWN',
          retryAfterSeconds: 90,
        },
        429,
      ),
    )

    const error = await apiGet('/players/p/refresh').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    const apiError = error as ApiError
    expect(apiError.status).toBe(429)
    expect(apiError.code).toBe('REFRESH_COOLDOWN')
    expect(apiError.retryAfterSeconds).toBe(90)
    expect(apiError.message).toContain('dans 90 s')
    expect(apiError.problem?.status).toBe(429)
  })

  it("garde un message par défaut si le corps de l'erreur n'est pas du JSON", async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response('<html>Bad Gateway</html>', { status: 502 }),
    )

    const error = (await apiGet('/ping').catch((e: unknown) => e)) as ApiError

    expect(error.status).toBe(502)
    expect(error.code).toBeUndefined()
    expect(error.message).toBe('GET /ping a échoué (502)')
  })

  it('transforme une erreur réseau en ApiError de statut 0', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'))

    const error = (await apiGet('/ping').catch((e: unknown) => e)) as ApiError

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(0)
    expect(error.message).toContain('API injoignable')
  })
})

describe('apiPost', () => {
  it('envoie un POST et renvoie le JSON', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ ok: true }))

    expect(await apiPost('/players/p/refresh')).toEqual({ ok: true })
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/players/p/refresh',
      expect.objectContaining({ method: 'POST' }),
    )
  })
})

describe('segment et query', () => {
  it('encode les caractères spéciaux du Riot ID', () => {
    expect(segment('Joueur Test')).toBe('Joueur%20Test')
    expect(segment('Été#1/2')).toBe('%C3%89t%C3%A9%231%2F2')
    expect(segment(420)).toBe('420')
  })

  it('omet les paramètres absents', () => {
    expect(query({ page: 0, size: 10, queue: undefined })).toBe('?page=0&size=10')
    expect(query({ queue: null })).toBe('')
    expect(query({})).toBe('')
  })
})
