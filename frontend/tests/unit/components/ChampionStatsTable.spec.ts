import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import type { Pinia } from 'pinia'

import ChampionStatsTable from '@/components/player/ChampionStatsTable.vue'
import { getChampionStats } from '@/api/players'
import type { ChampionStats } from '@/types/stats'
import { PUUID } from '../../fixtures/api'
import { loadedStaticData } from '../../fixtures/ddragon'

vi.mock('@/api/players')

let pinia: Pinia

const stat = (championId: number, championName: string, values: Partial<ChampionStats>) => ({
  championId,
  championName,
  games: 1,
  wins: 1,
  losses: 0,
  winrate: 100,
  avgKills: 5,
  avgDeaths: 2,
  avgAssists: 7,
  kda: 6,
  csPerMinute: 7,
  avgDamage: 20000,
  ...values,
})

/** Dans l'ordre de l'API (le plus joué d'abord). Wukong (id 62) s'appelle MonkeyKing dans les matchs. */
const STATS: ChampionStats[] = [
  stat(429, 'Kalista', { games: 10, wins: 6, losses: 4, winrate: 60, kda: 3.2, csPerMinute: 8.9 }),
  stat(62, 'MonkeyKing', { games: 4, wins: 1, losses: 3, winrate: 25, kda: 1.5, avgDamage: 31000 }),
  stat(1, 'Annie', { games: 4, wins: 3, losses: 1, winrate: 75, kda: 4.1, csPerMinute: 6.2 }),
]

async function mountTable() {
  const wrapper = mount(ChampionStatsTable, {
    props: { puuid: PUUID, refreshKey: 0 },
    global: { plugins: [pinia] },
  })
  await flushPromises()
  return wrapper
}

const names = (wrapper: Awaited<ReturnType<typeof mountTable>>) =>
  wrapper.findAll('[data-testid="champion-name"]').map((name) => name.text())

beforeEach(async () => {
  pinia = (await loadedStaticData()).pinia
  vi.mocked(getChampionStats).mockResolvedValue(STATS)
})

describe('ChampionStatsTable', () => {
  it("affiche les champions dans l'ordre de l'API, avec leur nom français", async () => {
    const wrapper = await mountTable()

    expect(getChampionStats).toHaveBeenCalledWith(PUUID, undefined)
    expect(names(wrapper)).toEqual(['Kalista', 'Wukong', 'Annie'])
    const kalista = wrapper.find('[data-testid="champion-row"]')
    expect(kalista.text()).toContain('6V 4D')
    expect(kalista.text().replace(/\s/g, ' ')).toContain('60 %')
    expect(kalista.text()).toContain('3,20')
    expect(kalista.text()).toContain('8,9')
    expect(wrapper.find('th[aria-sort="descending"]').text()).toContain('Parties')
  })

  it('trie par colonne, puis inverse au second clic', async () => {
    const wrapper = await mountTable()

    await wrapper.find('[data-testid="sort-winrate"]').trigger('click')
    expect(names(wrapper)).toEqual(['Annie', 'Kalista', 'Wukong'])
    expect(wrapper.find('th[aria-sort="descending"]').text()).toContain('Winrate')

    await wrapper.find('[data-testid="sort-winrate"]').trigger('click')
    expect(names(wrapper)).toEqual(['Wukong', 'Kalista', 'Annie'])
    expect(wrapper.find('th[aria-sort="ascending"]').text()).toContain('Winrate')

    await wrapper.find('[data-testid="sort-avgDamage"]').trigger('click')
    expect(names(wrapper)[0]).toBe('Wukong')
  })

  it('trie les noms de A à Z au premier clic', async () => {
    const wrapper = await mountTable()

    await wrapper.find('[data-testid="sort-champion"]').trigger('click')

    expect(names(wrapper)).toEqual(['Annie', 'Kalista', 'Wukong'])
  })

  it('à égalité, garde le champion le plus joué en premier', async () => {
    vi.mocked(getChampionStats).mockResolvedValue([
      stat(1, 'Annie', { games: 2, kda: 3 }),
      stat(429, 'Kalista', { games: 9, kda: 3 }),
    ])
    const wrapper = await mountTable()

    await wrapper.find('[data-testid="sort-kda"]').trigger('click')

    expect(names(wrapper)).toEqual(['Kalista', 'Annie'])
  })

  it('filtre par file en rechargeant les stats', async () => {
    const wrapper = await mountTable()
    const flex = wrapper.findAll('.champion-stats__filter')[2]

    await flex?.trigger('click')
    await flushPromises()

    expect(getChampionStats).toHaveBeenLastCalledWith(PUUID, 440)
    expect(flex?.attributes('aria-pressed')).toBe('true')
  })

  it('recharge après une mise à jour du joueur', async () => {
    const wrapper = await mountTable()

    await wrapper.setProps({ refreshKey: 1 })
    await flushPromises()

    expect(getChampionStats).toHaveBeenCalledTimes(2)
  })

  it('affiche un message sans partie dans la file choisie', async () => {
    vi.mocked(getChampionStats).mockResolvedValue([])
    const wrapper = await mountTable()

    expect(wrapper.find('[data-testid="champion-stats-empty"]').text()).toBe(
      'Aucune partie enregistrée.',
    )

    await wrapper.findAll('.champion-stats__filter')[1]?.trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="champion-stats-empty"]').text()).toBe(
      'Aucune partie enregistrée dans cette file.',
    )
  })
})
