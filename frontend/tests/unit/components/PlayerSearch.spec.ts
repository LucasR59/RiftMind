import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'

import PlayerSearch from '@/components/PlayerSearch.vue'
import RecentSearches from '@/components/RecentSearches.vue'
import {
  MAX_RECENT_SEARCHES,
  RECENT_SEARCHES_KEY,
  reloadRecentSearches,
  useRecentSearches,
} from '@/composables/useRecentSearches'
import { createAppRouter } from '@/router'
import { parseRiotIdInput } from '@/utils/riotId'

async function mountWithRouter<T>(component: T) {
  const router = createAppRouter(createMemoryHistory())
  await router.push('/')
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const wrapper = mount(component as any, { global: { plugins: [router] } })
  return { router, wrapper }
}

beforeEach(() => {
  localStorage.clear()
  reloadRecentSearches()
})

describe('parseRiotIdInput', () => {
  it('accepte « Nom#TAG », espaces autour du # compris', () => {
    expect(parseRiotIdInput('Joueur Test#EUW')).toEqual({
      ok: true,
      riotId: { gameName: 'Joueur Test', tagLine: 'EUW' },
    })
    expect(parseRiotIdInput('  Joueur Test #  EUW ')).toEqual({
      ok: true,
      riotId: { gameName: 'Joueur Test', tagLine: 'EUW' },
    })
    expect(parseRiotIdInput('페이커#KR1')).toMatchObject({ ok: true })
    expect(parseRiotIdInput('A#b#TAG')).toMatchObject({
      ok: true,
      riotId: { gameName: 'A#b', tagLine: 'TAG' },
    })
  })

  it.each([
    ['', 'Saisissez un Riot ID'],
    ['Joueur', 'Ajoutez le tag'],
    ['#EUW', 'nom avant le « # » est vide'],
    ['Joueur#', 'tag après le « # »'],
    ['Joueur#E', 'tag après le « # »'],
    ['Joueur#EUW123', 'tag après le « # »'],
    ['Joueur#EU W', 'tag après le « # »'],
    ['Un nom beaucoup trop long#EUW', 'ne doit pas dépasser 16 caractères'],
  ])('refuse « %s » avec un message clair', (input, message) => {
    const result = parseRiotIdInput(input)

    expect(result.ok).toBe(false)
    expect(result.ok ? '' : result.error).toContain(message)
  })
})

describe('useRecentSearches', () => {
  it('garde les recherches de la plus récente à la plus ancienne, sans doublon de casse', () => {
    const { items, add } = useRecentSearches()

    add({ platform: 'euw1', gameName: 'Joueur A', tagLine: 'EUW' })
    add({ platform: 'kr', gameName: 'Joueur B', tagLine: 'KR1' })
    add({ platform: 'euw1', gameName: 'joueur a', tagLine: 'euw' })

    expect(items.value.map((s) => s.gameName)).toEqual(['joueur a', 'Joueur B'])
    expect(JSON.parse(localStorage.getItem(RECENT_SEARCHES_KEY) ?? '[]')).toHaveLength(2)
  })

  it('distingue le même Riot ID sur deux plateformes', () => {
    const { items, add } = useRecentSearches()

    add({ platform: 'euw1', gameName: 'Joueur', tagLine: 'TAG' })
    add({ platform: 'na1', gameName: 'Joueur', tagLine: 'TAG' })

    expect(items.value).toHaveLength(2)
  })

  it(`n'en garde que ${MAX_RECENT_SEARCHES}`, () => {
    const { items, add } = useRecentSearches()

    for (let i = 1; i <= MAX_RECENT_SEARCHES + 3; i++) {
      add({ platform: 'euw1', gameName: `Joueur ${i}`, tagLine: 'EUW' })
    }

    expect(items.value).toHaveLength(MAX_RECENT_SEARCHES)
    expect(items.value[0]?.gameName).toBe(`Joueur ${MAX_RECENT_SEARCHES + 3}`)
  })

  it('ignore un contenu de localStorage abîmé', () => {
    localStorage.setItem(RECENT_SEARCHES_KEY, '{pas du json')
    reloadRecentSearches()
    expect(useRecentSearches().items.value).toEqual([])

    localStorage.setItem(RECENT_SEARCHES_KEY, JSON.stringify([{ platform: 'euw1' }, 42]))
    reloadRecentSearches()
    expect(useRecentSearches().items.value).toEqual([])
  })
})

describe('PlayerSearch', () => {
  it('ouvre le profil du joueur et retient la recherche', async () => {
    const { router, wrapper } = await mountWithRouter(PlayerSearch)

    await wrapper.find('select').setValue('kr')
    await wrapper.find('[data-testid="search-input"]').setValue('Été Test#KR1')
    await wrapper.find('form').trigger('submit')
    // La page du profil est chargée à la demande : attendre la fin de la navigation
    await vi.waitFor(() => expect(router.currentRoute.value.name).toBe('player'))
    expect(router.currentRoute.value.params).toEqual({ platform: 'kr', riotId: 'Été Test-KR1' })
    expect(useRecentSearches().items.value[0]).toEqual({
      platform: 'kr',
      gameName: 'Été Test',
      tagLine: 'KR1',
    })
  })

  it("affiche l'erreur de format sans changer de page", async () => {
    const { router, wrapper } = await mountWithRouter(PlayerSearch)

    await wrapper.find('[data-testid="search-input"]').setValue('Joueur')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const error = wrapper.find('[data-testid="search-error"]')
    expect(error.text()).toContain('Ajoutez le tag')
    expect(wrapper.find('input').attributes('aria-invalid')).toBe('true')
    expect(wrapper.find('input').attributes('aria-describedby')).toBe(error.attributes('id'))
    expect(router.currentRoute.value.name).toBe('home')
    expect(useRecentSearches().items.value).toEqual([])
  })

  it('efface l’erreur dès que la saisie change', async () => {
    const { wrapper } = await mountWithRouter(PlayerSearch)
    await wrapper.find('form').trigger('submit')

    await wrapper.find('[data-testid="search-input"]').setValue('J')

    expect(wrapper.find('[data-testid="search-error"]').exists()).toBe(false)
  })

  it('propose la région de la dernière recherche', async () => {
    useRecentSearches().add({ platform: 'na1', gameName: 'Joueur', tagLine: 'NA1' })

    const { wrapper } = await mountWithRouter(PlayerSearch)

    expect((wrapper.find('select').element as HTMLSelectElement).value).toBe('na1')
  })
})

describe('RecentSearches', () => {
  it("n'affiche rien sans recherche", async () => {
    const { wrapper } = await mountWithRouter(RecentSearches)

    expect(wrapper.find('section').exists()).toBe(false)
  })

  it('affiche des liens vers les profils, retirables un à un ou tous ensemble', async () => {
    const { add } = useRecentSearches()
    add({ platform: 'euw1', gameName: 'Joueur Test', tagLine: 'EUW' })
    add({ platform: 'kr', gameName: 'Faker', tagLine: 'KR1' })
    const { wrapper } = await mountWithRouter(RecentSearches)

    const items = wrapper.findAll('[data-testid="recent-search"]')
    expect(items).toHaveLength(2)
    expect(items[0]?.text()).toContain('KR')
    expect(items[0]?.text()).toContain('Faker#KR1')
    expect(items[1]?.find('a').attributes('href')).toBe('/euw1/Joueur%20Test-EUW')

    await items[0]?.find('button').trigger('click')
    expect(wrapper.findAll('[data-testid="recent-search"]')).toHaveLength(1)

    await wrapper.find('.recent__clear').trigger('click')
    expect(wrapper.find('section').exists()).toBe(false)
    expect(localStorage.getItem(RECENT_SEARCHES_KEY)).toBe('[]')
  })
})
