import { describe, it, expect, vi } from 'vitest'

import { getChampionStats, getPlayer, getPlayerSummary, refreshPlayer } from '@/api/players'
import { ApiError } from '@/api/http'
import { championStats, jsonResponse, player, playerSummary, PUUID } from '../../fixtures/api'

const mockFetch = (body: unknown, status = 200) =>
  vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(body, status))

type FetchMock = ReturnType<typeof mockFetch>
const calledUrl = (fetchMock: FetchMock, call = 0) => fetchMock.mock.calls[call]?.[0]
const calledMethod = (fetchMock: FetchMock) => fetchMock.mock.calls[0]?.[1]?.method

describe('getPlayer', () => {
  it("cherche un joueur par Riot ID, encodé dans l'URL", async () => {
    const fetchMock = mockFetch(player)

    expect(await getPlayer('euw1', 'Joueur Test', 'EUW')).toEqual(player)
    expect(calledUrl(fetchMock)).toBe('/api/players/euw1/Joueur%20Test/EUW')
    expect(calledMethod(fetchMock)).toBe('GET')
  })

  it('encode les accents et les caractères réservés', async () => {
    const fetchMock = mockFetch(player)

    await getPlayer('kr', 'Été?', '#1')

    expect(calledUrl(fetchMock)).toBe('/api/players/kr/%C3%89t%C3%A9%3F/%231')
  })

  it('renvoie une ApiError NOT_FOUND pour un joueur inconnu', async () => {
    mockFetch(
      { code: 'NOT_FOUND', detail: 'Aucun joueur « Inconnu#EUW » sur EUW (Europe Ouest).' },
      404,
    )

    await expect(getPlayer('euw1', 'Inconnu', 'EUW')).rejects.toMatchObject({
      status: 404,
      code: 'NOT_FOUND',
      message: 'Aucun joueur « Inconnu#EUW » sur EUW (Europe Ouest).',
    })
  })
})

describe('refreshPlayer', () => {
  it('envoie un POST sur /refresh', async () => {
    const fetchMock = mockFetch(player)

    expect(await refreshPlayer(PUUID)).toEqual(player)
    expect(calledUrl(fetchMock)).toBe(`/api/players/${PUUID}/refresh`)
    expect(calledMethod(fetchMock)).toBe('POST')
  })

  it('expose le temps restant quand la mise à jour est trop récente', async () => {
    mockFetch({ code: 'REFRESH_COOLDOWN', retryAfterSeconds: 42 }, 429)

    const error = (await refreshPlayer(PUUID).catch((e: unknown) => e)) as ApiError

    expect(error.code).toBe('REFRESH_COOLDOWN')
    expect(error.retryAfterSeconds).toBe(42)
  })
})

describe('statistiques', () => {
  it('demande les stats par champion, avec ou sans file', async () => {
    const fetchMock = mockFetch(championStats)

    expect(await getChampionStats(PUUID, 420)).toEqual(championStats)
    expect(calledUrl(fetchMock)).toBe(`/api/players/${PUUID}/champions?queue=420`)

    fetchMock.mockResolvedValue(jsonResponse(championStats))
    await getChampionStats(PUUID)
    expect(calledUrl(fetchMock, 1)).toBe(`/api/players/${PUUID}/champions`)
  })

  it('demande le résumé des dernières parties', async () => {
    const fetchMock = mockFetch(playerSummary)

    expect(await getPlayerSummary(PUUID)).toEqual(playerSummary)
    expect(calledUrl(fetchMock)).toBe(`/api/players/${PUUID}/summary`)
  })
})
