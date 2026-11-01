import { describe, it, expect, vi } from 'vitest'

import { apiGet, ApiError } from '@/api/http'

describe('apiGet', () => {
  it('préfixe le chemin avec /api et renvoie le JSON', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify({ status: 'ok' }), { status: 200 }))

    const result = await apiGet<{ status: string }>('/ping')

    expect(result).toEqual({ status: 'ok' })
    expect(fetchMock).toHaveBeenCalledWith('/api/ping', expect.any(Object))
  })

  it("lève une ApiError avec le code HTTP en cas d'échec", async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 503 }))

    await expect(apiGet('/ping')).rejects.toMatchObject({ name: 'ApiError', status: 503 })
    await expect(apiGet('/ping')).rejects.toBeInstanceOf(ApiError)
  })
})
