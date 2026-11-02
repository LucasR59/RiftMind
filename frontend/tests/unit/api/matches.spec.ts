import { describe, it, expect, vi } from 'vitest'

import { getMatch, getPlayerMatches } from '@/api/matches'
import { jsonResponse, MATCH_ID, matchDetail, matchPage, PUUID } from '../../fixtures/api'

const mockFetch = (body: unknown, status = 200) =>
  vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(body, status))

describe('getPlayerMatches', () => {
  it('demande la première page par défaut', async () => {
    const fetchMock = mockFetch(matchPage)

    expect(await getPlayerMatches(PUUID)).toEqual(matchPage)
    expect(fetchMock.mock.calls[0]?.[0]).toBe(`/api/players/${PUUID}/matches`)
  })

  it('transmet la page, la taille et la file', async () => {
    const fetchMock = mockFetch(matchPage)

    await getPlayerMatches(PUUID, { page: 2, size: 5, queue: 440 })

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      `/api/players/${PUUID}/matches?page=2&size=5&queue=440`,
    )
  })
})

describe('getMatch', () => {
  it("demande le détail d'une partie", async () => {
    const fetchMock = mockFetch(matchDetail)

    expect(await getMatch(MATCH_ID)).toEqual(matchDetail)
    expect(fetchMock.mock.calls[0]?.[0]).toBe(`/api/matches/${MATCH_ID}`)
  })

  it('renvoie une ApiError 404 pour une partie inconnue', async () => {
    mockFetch({ code: 'NOT_FOUND', detail: 'Partie « EUW1_404 » introuvable.' }, 404)

    await expect(getMatch('EUW1_404')).rejects.toMatchObject({ status: 404, code: 'NOT_FOUND' })
  })
})
