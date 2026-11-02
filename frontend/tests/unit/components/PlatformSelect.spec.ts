import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'

import PlatformSelect from '@/components/PlatformSelect.vue'
import { findPlatform, PLATFORMS, platformsByRegion } from '@/constants/platforms'

describe('constantes des plateformes', () => {
  it('contient les 15 plateformes du backend, sans doublon', () => {
    expect(PLATFORMS).toHaveLength(15)
    expect(new Set(PLATFORMS.map((p) => p.id)).size).toBe(15)
    expect(new Set(PLATFORMS.map((p) => p.shortName)).size).toBe(15)
    expect(PLATFORMS.map((p) => p.id)).not.toContain('ph2')
  })

  it('retrouve une plateforme par identifiant ou abréviation, sans tenir compte de la casse', () => {
    expect(findPlatform('euw1')?.shortName).toBe('EUW')
    expect(findPlatform('EUW')?.id).toBe('euw1')
    expect(findPlatform(' lan ')?.id).toBe('la1')
    expect(findPlatform('SEA')?.region).toBe('sea')
    expect(findPlatform('europe')).toBeUndefined()
    expect(findPlatform('')).toBeUndefined()
    expect(findPlatform(undefined)).toBeUndefined()
  })

  it('groupe les plateformes par région, Europe en premier', () => {
    const groups = platformsByRegion()

    expect(groups.map((g) => g.region)).toEqual(['europe', 'americas', 'asia', 'sea'])
    expect(groups.flatMap((g) => g.platforms)).toHaveLength(15)
    expect(groups[0]?.platforms.map((p) => p.id)).toContain('euw1')
  })
})

describe('PlatformSelect', () => {
  it('propose les 15 plateformes groupées par région, EUW par défaut', () => {
    const wrapper = mount(PlatformSelect)

    expect(wrapper.findAll('optgroup')).toHaveLength(4)
    expect(wrapper.findAll('option')).toHaveLength(15)
    expect((wrapper.element as HTMLSelectElement).value).toBe('euw1')
    expect(wrapper.attributes('aria-label')).toBe('Région')
    expect(wrapper.find('option[value="kr"]').text()).toBe('KR — Corée')
  })

  it('met à jour le v-model quand on change de plateforme', async () => {
    const wrapper = mount(PlatformSelect, {
      props: {
        modelValue: 'euw1',
        'onUpdate:modelValue': (value: string | undefined) =>
          wrapper.setProps({ modelValue: value }),
      },
    })

    await wrapper.setValue('na1')

    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['na1'])
    expect(wrapper.props('modelValue')).toBe('na1')
  })

  it('affiche la valeur reçue', () => {
    const wrapper = mount(PlatformSelect, { props: { modelValue: 'kr' } })

    expect((wrapper.element as HTMLSelectElement).value).toBe('kr')
  })
})
