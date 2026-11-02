import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { CACHE_PREFIX, compareVersionsDesc, useStaticDataStore } from '@/stores/staticData'
import { DDRAGON_BASE_URL } from '@/api/ddragon'
import { mockDataDragon } from '../../fixtures/ddragon'

const DD = DDRAGON_BASE_URL

const dataFileCalls = (fetchMock: ReturnType<typeof mockDataDragon>) =>
  fetchMock.mock.calls.filter(([url]) => String(url).includes('/data/')).length

describe('store staticData', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('charge la dernière version et construit les données compactes', async () => {
    const fetchMock = mockDataDragon(['16.21.1', '16.20.1'])
    const store = useStaticDataStore()

    await store.load()

    expect(store.status).toBe('ready')
    expect(store.version).toBe('16.21.1')
    expect(dataFileCalls(fetchMock)).toBe(4)
    expect(fetchMock).toHaveBeenCalledWith(`${DD}/cdn/16.21.1/data/fr_FR/champion.json`)
    expect(store.champion(62)).toEqual({ id: 'MonkeyKing', name: 'Wukong' })
    expect(store.item(3087)?.name).toBe('Poignard de Statikk')
    expect(store.item(1001)?.plaintext).toBe('')
    expect(store.spell(4)?.name).toBe('Saut éclair')
    expect(store.rune(8112)?.name).toBe('Électrocution')
    expect(store.rune(8100)?.name).toBe('Domination')
    expect(store.champion(9999)).toBeNull()
  })

  it('construit les URL des images', async () => {
    mockDataDragon(['16.21.1'])
    const store = useStaticDataStore()
    await store.load()

    expect(store.championIconUrl(62)).toBe(`${DD}/cdn/16.21.1/img/champion/MonkeyKing.png`)
    expect(store.itemIconUrl(3087)).toBe(`${DD}/cdn/16.21.1/img/item/3087.png`)
    expect(store.itemIconUrl(0)).toBeNull()
    expect(store.spellIconUrl(4)).toBe(`${DD}/cdn/16.21.1/img/spell/SummonerFlash.png`)
    expect(store.runeIconUrl(8112)).toBe(
      `${DD}/cdn/img/perk-images/Styles/Domination/Electrocute/Electrocute.png`,
    )
    expect(store.profileIconUrl(7)).toBe(`${DD}/cdn/16.21.1/img/profileicon/7.png`)
  })

  it('utilise le nom du match pour un champion absent de Data Dragon (patch récent)', async () => {
    mockDataDragon(['16.21.1'])
    const store = useStaticDataStore()
    await store.load()

    expect(store.championIconUrl(999, 'NouveauChampion')).toBe(
      `${DD}/cdn/16.21.1/img/champion/NouveauChampion.png`,
    )
    expect(store.championIconUrl(999)).toBeNull()
  })

  it('renvoie null pour les images tant que les données ne sont pas chargées', () => {
    const store = useStaticDataStore()

    expect(store.championIconUrl(429, 'Kalista')).toBeNull()
    expect(store.itemIconUrl(3087)).toBeNull()
    expect(store.profileIconUrl(7)).toBeNull()
  })

  it('reprend une version déjà en cache sans retélécharger les fichiers', async () => {
    mockDataDragon(['16.21.1'])
    await useStaticDataStore().load()
    expect(localStorage.getItem(`${CACHE_PREFIX}16.21.1`)).not.toBeNull()

    // Nouveau chargement de l'application
    vi.restoreAllMocks()
    setActivePinia(createPinia())
    const fetchMock = mockDataDragon(['16.21.1'])
    const store = useStaticDataStore()
    await store.load()

    expect(dataFileCalls(fetchMock)).toBe(0)
    expect(store.champion(429)?.name).toBe('Kalista')
  })

  it("télécharge un nouveau patch et supprime l'ancienne version du cache", async () => {
    mockDataDragon(['16.21.1'])
    await useStaticDataStore().load()

    vi.restoreAllMocks()
    setActivePinia(createPinia())
    const fetchMock = mockDataDragon(['16.22.1', '16.21.1'])
    const store = useStaticDataStore()
    await store.load()

    expect(store.version).toBe('16.22.1')
    expect(dataFileCalls(fetchMock)).toBe(4)
    expect(localStorage.getItem(`${CACHE_PREFIX}16.22.1`)).not.toBeNull()
    expect(localStorage.getItem(`${CACHE_PREFIX}16.21.1`)).toBeNull()
  })

  it('utilise la version en cache si Data Dragon ne répond pas', async () => {
    mockDataDragon(['16.21.1'])
    await useStaticDataStore().load()

    vi.restoreAllMocks()
    setActivePinia(createPinia())
    mockDataDragon(null)
    const store = useStaticDataStore()
    await store.load()

    expect(store.status).toBe('ready')
    expect(store.version).toBe('16.21.1')
  })

  it("passe en erreur si Data Dragon ne répond pas et que rien n'est en cache", async () => {
    mockDataDragon(null)
    const store = useStaticDataStore()

    await store.load()

    expect(store.status).toBe('error')
    expect(store.error).toContain('versions.json')
  })

  it('ne charge les données qu’une fois, même avec plusieurs appels simultanés', async () => {
    const fetchMock = mockDataDragon(['16.21.1'])
    const store = useStaticDataStore()

    await Promise.all([store.load(), store.load(), store.load()])
    await store.load()

    expect(
      fetchMock.mock.calls.filter(([url]) => String(url).endsWith('versions.json')),
    ).toHaveLength(1)
    expect(dataFileCalls(fetchMock)).toBe(4)
  })

  it('donne le nom français des files de jeu', () => {
    const store = useStaticDataStore()

    expect(store.queue(420)).toEqual({ label: 'Classée Solo/Duo', short: 'SoloQ' })
    expect(store.queue(440).short).toBe('Flex')
    expect(store.queue(450).label).toBe('ARAM')
    expect(store.queue(4242).label).toBe('File 4242')
  })
})

describe('compareVersionsDesc', () => {
  it('trie les versions numériquement, la plus récente en premier', () => {
    expect(['16.9.1', '16.21.1', '15.24.1'].sort(compareVersionsDesc)).toEqual([
      '16.21.1',
      '16.9.1',
      '15.24.1',
    ])
  })
})
