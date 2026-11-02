// Data Dragon simulé pour les tests : extraits des vrais fichiers 16.21.1 (fr_FR).

import { vi } from 'vitest'

export const DD = 'https://ddragon.leagueoflegends.com'

/** Extraits des vrais fichiers Data Dragon 16.21.1 (fr_FR). */
export const FILES: Record<string, unknown> = {
  'champion.json': {
    data: {
      Kalista: { id: 'Kalista', key: '429', name: 'Kalista' },
      MonkeyKing: { id: 'MonkeyKing', key: '62', name: 'Wukong' },
    },
  },
  'item.json': {
    data: {
      '3087': { name: 'Poignard de Statikk', plaintext: 'Les déplacements génèrent des charges.' },
      '1001': { name: 'Bottes' },
    },
  },
  'summoner.json': {
    data: {
      SummonerFlash: {
        id: 'SummonerFlash',
        key: '4',
        name: 'Saut éclair',
        image: { full: 'SummonerFlash.png' },
      },
    },
  },
  'runesReforged.json': [
    {
      id: 8100,
      icon: 'perk-images/Styles/7200_Domination.png',
      name: 'Domination',
      slots: [
        {
          runes: [
            {
              id: 8112,
              icon: 'perk-images/Styles/Domination/Electrocute/Electrocute.png',
              name: 'Électrocution',
              shortDesc: 'Toucher un champion avec 3 attaques…',
            },
          ],
        },
      ],
    },
  ],
}

/** Simule Data Dragon : `versions` est la liste renvoyée par versions.json, ou `null` pour une panne. */
export function mockDataDragon(versions: string[] | null) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    const url = String(input)
    if (url === `${DD}/api/versions.json`) {
      return versions ? Response.json(versions) : new Response(null, { status: 503 })
    }
    const file = url.split('/').pop() ?? ''
    if (url.includes('/data/fr_FR/') && file in FILES) {
      return Response.json(FILES[file])
    }
    return new Response(null, { status: 404 })
  })
}

/**
 * Pinia neuve et données Data Dragon chargées (version 16.21.1), pour tester les composants qui affichent des
 * images du jeu. À appeler dans `beforeEach`, puis monter les composants avec `global: { plugins: [pinia] }`.
 */
export async function loadedStaticData() {
  const { createPinia, setActivePinia } = await import('pinia')
  const { useStaticDataStore } = await import('@/stores/staticData')
  const pinia = createPinia()
  setActivePinia(pinia)
  mockDataDragon(['16.21.1'])
  const store = useStaticDataStore()
  await store.load()
  return { pinia, store }
}
