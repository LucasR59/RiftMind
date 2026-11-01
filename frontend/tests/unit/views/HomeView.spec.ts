import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'

import HomeView from '@/views/HomeView.vue'

// Module mocké automatiquement : on teste uniquement le rendu de la page
vi.mock('@/api/ping')

describe('HomeView', () => {
  it("affiche le nom RiftMind et le statut de l'API", () => {
    const wrapper = mount(HomeView)

    expect(wrapper.find('h1').text()).toBe('RiftMind')
    expect(wrapper.find('[data-testid="backend-status"]').exists()).toBe(true)
  })
})
