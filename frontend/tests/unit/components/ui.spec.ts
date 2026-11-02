import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import AppTooltip from '@/components/ui/AppTooltip.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import LoadingSkeleton from '@/components/ui/LoadingSkeleton.vue'

describe('AppTooltip', () => {
  it('relie le contenu à son info-bulle', () => {
    const wrapper = mount(AppTooltip, {
      props: { text: 'Saut éclair' },
      slots: { default: '<img alt="Flash" />' },
    })

    const bubble = wrapper.find('[role="tooltip"]')
    expect(bubble.text()).toBe('Saut éclair')
    expect(wrapper.attributes('aria-describedby')).toBe(bubble.attributes('id'))
    expect(wrapper.find('img').exists()).toBe(true)
  })

  it('accepte un contenu riche', () => {
    const wrapper = mount(AppTooltip, {
      slots: { default: 'x', content: '<strong>Titre</strong> description' },
    })

    expect(wrapper.find('[role="tooltip"] strong').text()).toBe('Titre')
  })

  it("n'affiche pas d'info-bulle sans texte", () => {
    const wrapper = mount(AppTooltip, { slots: { default: 'x' } })

    expect(wrapper.find('[role="tooltip"]').exists()).toBe(false)
    expect(wrapper.attributes('aria-describedby')).toBeUndefined()
  })
})

describe('LoadingSkeleton', () => {
  it('est masqué aux lecteurs d’écran et affiche le nombre de lignes demandé', () => {
    const wrapper = mount(LoadingSkeleton, { props: { lines: 3 } })

    expect(wrapper.attributes('aria-hidden')).toBe('true')
    expect(wrapper.findAll('.skeleton')).toHaveLength(3)
  })

  it('utilise la largeur comme diamètre pour un cercle', () => {
    const wrapper = mount(LoadingSkeleton, { props: { shape: 'circle', width: '48px' } })

    const style = wrapper.find('.skeleton').attributes('style')
    expect(style).toContain('width: 48px')
    expect(style).toContain('height: 48px')
  })
})

describe('ErrorState', () => {
  it('affiche le titre et le message dans une alerte', () => {
    const wrapper = mount(ErrorState, {
      props: { title: 'Joueur introuvable', message: 'Aucun joueur « X#Y » sur EUW.' },
    })

    expect(wrapper.attributes('role')).toBe('alert')
    expect(wrapper.text()).toContain('Joueur introuvable')
    expect(wrapper.text()).toContain('Aucun joueur « X#Y » sur EUW.')
    expect(wrapper.find('button').exists()).toBe(false)
  })

  it('émet retry au clic sur « Réessayer »', async () => {
    const wrapper = mount(ErrorState, { props: { retryable: true } })

    await wrapper.find('button').trigger('click')

    expect(wrapper.find('button').text()).toBe('Réessayer')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })

  it('peut désactiver le bouton (compte à rebours)', () => {
    const wrapper = mount(ErrorState, {
      props: { retryable: true, retryDisabled: true, retryLabel: 'Réessayer dans 12 s' },
    })

    expect(wrapper.find('button').attributes('disabled')).toBeDefined()
    expect(wrapper.find('button').text()).toBe('Réessayer dans 12 s')
  })
})
