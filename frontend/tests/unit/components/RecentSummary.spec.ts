import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

import RecentSummary from '@/components/player/RecentSummary.vue'
import { getPlayerSummary } from '@/api/players'
import { playerSummary, PUUID } from '../../fixtures/api'

vi.mock('@/api/players')

async function mountSummary(refreshKey = 0) {
  const wrapper = mount(RecentSummary, { props: { puuid: PUUID, refreshKey } })
  await flushPromises()
  return wrapper
}

describe('RecentSummary', () => {
  it('affiche le bilan, le winrate, le KDA et les rôles des 20 dernières parties', async () => {
    vi.mocked(getPlayerSummary).mockResolvedValue(playerSummary)

    const wrapper = await mountSummary()

    expect(getPlayerSummary).toHaveBeenCalledWith(PUUID)
    expect(wrapper.find('h2').text()).toBe('20 dernières parties')
    expect(wrapper.text()).toContain('12V')
    expect(wrapper.text()).toContain('8D')
    expect(wrapper.find('[data-testid="summary-winrate"]').text().replace(/\s/g, ' ')).toBe('60 %')
    expect(wrapper.find('.summary__bar-fill').attributes('style')).toContain('width: 60%')
    const kda = wrapper.find('[data-testid="summary-kda"]')
    expect(kda.find('.summary__kda-ratio').text()).toBe('3,50 KDA')
    expect(kda.find('.summary__kda-detail').text().replace(/\s+/g, ' ')).toBe('6,5 / 4 / 7,5')
    const roles = wrapper.findAll('[data-testid="summary-role"]')
    expect(roles.map((r) => r.find('.summary__role-name').text())).toEqual(['Bas', 'Support'])
    expect(roles.map((r) => r.find('.summary__role-games').text())).toEqual(['15', '5'])
    expect(roles[0]?.find('.summary__role-fill').attributes('style')).toContain('width: 75%')
  })

  it('affiche un message pour un joueur sans partie récente', async () => {
    vi.mocked(getPlayerSummary).mockResolvedValue({
      ...playerSummary,
      games: 0,
      wins: 0,
      losses: 0,
      roles: [],
    })

    const wrapper = await mountSummary()

    expect(wrapper.find('[data-testid="summary-empty"]').exists()).toBe(true)
    expect(wrapper.find('h2').text()).toBe('Dernières parties')
  })

  it('recharge après une mise à jour, et permet de réessayer après une erreur', async () => {
    vi.mocked(getPlayerSummary).mockRejectedValueOnce(new Error('réseau'))
    const wrapper = await mountSummary()
    expect(wrapper.find('[data-testid="error-state"]').exists()).toBe(true)

    vi.mocked(getPlayerSummary).mockResolvedValue(playerSummary)
    await wrapper.find('[data-testid="error-state"] button').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="summary-winrate"]').exists()).toBe(true)

    await wrapper.setProps({ refreshKey: 1 })
    await flushPromises()
    expect(getPlayerSummary).toHaveBeenCalledTimes(3)
  })
})
