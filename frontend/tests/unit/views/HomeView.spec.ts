import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'

import HomeView from '@/views/HomeView.vue'
import { createAppRouter } from '@/router'

// Module mocké automatiquement : on teste uniquement le rendu de la page
vi.mock('@/api/ping')

describe('HomeView', () => {
  it("affiche le nom RiftMind, la recherche de joueur et le statut de l'API", () => {
    const router = createAppRouter(createMemoryHistory())
    const wrapper = mount(HomeView, { global: { plugins: [router] } })

    expect(wrapper.find('h1').text()).toBe('RiftMind')
    expect(wrapper.find('form[role="search"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="platform-select"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="backend-status"]').exists()).toBe(true)
  })
})
