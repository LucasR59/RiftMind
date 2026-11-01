import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'

import AppFooter from '@/components/layout/AppFooter.vue'

describe('AppFooter', () => {
  it('affiche la mention légale exigée par Riot Games', () => {
    const notice = mount(AppFooter).find('[data-testid="riot-legal-notice"]')

    expect(notice.exists()).toBe(true)
    expect(notice.text()).toContain("RiftMind isn't endorsed by Riot Games")
    expect(notice.text()).toContain('trademarks or registered trademarks of Riot Games, Inc.')
  })

  it("affiche le copyright de l'année en cours", () => {
    const year = new Date().getFullYear().toString()

    expect(mount(AppFooter).text()).toContain(`© ${year} RiftMind`)
  })
})
