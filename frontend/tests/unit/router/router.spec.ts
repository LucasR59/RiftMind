import { describe, it, expect } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import { mount } from '@vue/test-utils'

import { createAppRouter, playerRoute } from '@/router'
import { formatRiotId, fromRiotIdSlug, toRiotIdSlug } from '@/utils/riotId'
import PlayerView from '@/views/PlayerView.vue'

const newRouter = () => createAppRouter(createMemoryHistory())

describe('Riot ID dans l’URL', () => {
  it('convertit « Nom#TAG » en « Nom-TAG » et inversement', () => {
    const riotId = { gameName: 'Joueur Test', tagLine: 'EUW' }

    expect(toRiotIdSlug(riotId)).toBe('Joueur Test-EUW')
    expect(fromRiotIdSlug('Joueur Test-EUW')).toEqual(riotId)
    expect(formatRiotId(riotId)).toBe('Joueur Test#EUW')
  })

  it('coupe au dernier tiret : le nom peut en contenir, pas le tag', () => {
    expect(fromRiotIdSlug('Jean-Pierre-FR1')).toEqual({ gameName: 'Jean-Pierre', tagLine: 'FR1' })
  })

  it('refuse un Riot ID sans nom ou sans tag', () => {
    expect(fromRiotIdSlug('SansTag')).toBeNull()
    expect(fromRiotIdSlug('-EUW')).toBeNull()
    expect(fromRiotIdSlug('Nom-')).toBeNull()
  })
})

describe('route du profil /:platform/:riotId', () => {
  it('construit le lien d’un joueur, encodé', () => {
    const router = newRouter()

    expect(
      router.resolve(playerRoute('euw1', { gameName: 'Joueur Test', tagLine: 'EUW' })).href,
    ).toBe('/euw1/Joueur%20Test-EUW')
  })

  it.each([
    ['accents', 'Été Ünïcödé'],
    ['caractères réservés', 'A/B#C?D&E%F'],
    ['écritures non latines', '페이커 フェイカー'],
  ])('fait l’aller-retour avec des %s dans le nom', async (_, gameName) => {
    const router = newRouter()
    const target = playerRoute('kr', { gameName, tagLine: 'KR1' })

    await router.push(target)

    expect(router.currentRoute.value.name).toBe('player')
    expect(fromRiotIdSlug(String(router.currentRoute.value.params.riotId))).toEqual({
      gameName,
      tagLine: 'KR1',
    })
    // Et depuis l'URL tapée ou partagée
    const fromUrl = newRouter()
    await fromUrl.push(router.resolve(target).fullPath)
    expect(fromUrl.currentRoute.value.params.riotId).toBe(`${gameName}-KR1`)
  })

  it('redirige une abréviation ou des majuscules vers l’URL canonique', async () => {
    const router = newRouter()

    await router.push('/EUW/Joueur%20Test-EUW')

    expect(router.currentRoute.value.fullPath).toBe('/euw1/Joueur%20Test-EUW')
    expect(router.currentRoute.value.params.platform).toBe('euw1')
  })

  it.each(['/xx/Joueur-EUW', '/europe/Joueur-EUW', '/euw1/SansTag', '/euw1/Joueur-'])(
    'affiche la page introuvable pour %s, en gardant l’URL',
    async (path) => {
      const router = newRouter()

      await router.push(path)

      expect(router.currentRoute.value.name).toBe('not-found')
      expect(router.currentRoute.value.path).toBe(path)
    },
  )

  it("met le Riot ID dans le titre de l'onglet", async () => {
    const router = newRouter()

    await router.push('/euw1/Joueur%20Test-EUW')

    expect(document.title).toBe('Joueur Test#EUW · RiftMind')
  })

  it('affiche le Riot ID et la plateforme sur la page du profil', async () => {
    const router = newRouter()
    await router.push('/kr/%C3%89t%C3%A9-KR1')

    const wrapper = mount(PlayerView, { global: { plugins: [router] } })

    expect(wrapper.find('[data-testid="player-riot-id"]').text()).toBe('Été#KR1')
    expect(wrapper.text()).toContain('KR — Corée')
  })
})
