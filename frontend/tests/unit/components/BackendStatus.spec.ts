import { describe, it, expect, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

import BackendStatus from '@/components/BackendStatus.vue'
import { fetchPing } from '@/api/ping'

vi.mock('@/api/ping')

describe('BackendStatus', () => {
  it("affiche « API connectée » quand l'API répond ok", async () => {
    vi.mocked(fetchPing).mockResolvedValue({
      status: 'ok',
      service: 'riftmind-backend',
      timestamp: '2026-01-01T00:00:00Z',
    })

    const wrapper = mount(BackendStatus)
    expect(wrapper.text()).toContain('Connexion')

    await flushPromises()
    expect(wrapper.text()).toContain('API connectée')
    expect(wrapper.classes()).toContain('status--up')
  })

  it("affiche « API injoignable » quand l'appel échoue", async () => {
    vi.mocked(fetchPing).mockRejectedValue(new Error('réseau'))

    const wrapper = mount(BackendStatus)
    await flushPromises()

    expect(wrapper.text()).toContain('API injoignable')
    expect(wrapper.classes()).toContain('status--down')
  })
})
