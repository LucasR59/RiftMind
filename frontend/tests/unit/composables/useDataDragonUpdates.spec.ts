import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent } from 'vue'

import { useDataDragonUpdates } from '@/composables/useDataDragonUpdates'
import { useStaticDataStore } from '@/stores/staticData'

const Host = defineComponent({
  setup() {
    useDataDragonUpdates()
    return () => null
  },
})

function setVisibility(state: DocumentVisibilityState) {
  Object.defineProperty(document, 'visibilityState', { value: state, configurable: true })
  document.dispatchEvent(new Event('visibilitychange'))
}

describe('useDataDragonUpdates', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.useRealTimers()
    setVisibility('visible')
  })

  it('charge les données au démarrage puis revérifie quand l’onglet redevient visible', () => {
    const store = useStaticDataStore()
    const load = vi.spyOn(store, 'load').mockResolvedValue()
    const check = vi.spyOn(store, 'checkForUpdate').mockResolvedValue(false)
    const wrapper = mount(Host)

    expect(load).toHaveBeenCalledTimes(1)

    setVisibility('hidden')
    expect(check).not.toHaveBeenCalled()
    setVisibility('visible')
    expect(check).toHaveBeenCalledTimes(1)

    wrapper.unmount()
    setVisibility('visible')
    expect(check).toHaveBeenCalledTimes(1)
  })

  it('revérifie régulièrement tant que l’onglet est visible', () => {
    const store = useStaticDataStore()
    vi.spyOn(store, 'load').mockResolvedValue()
    const check = vi.spyOn(store, 'checkForUpdate').mockResolvedValue(false)
    mount(Host)

    vi.advanceTimersByTime(15 * 60 * 1000)

    expect(check).toHaveBeenCalledTimes(1)
  })
})
