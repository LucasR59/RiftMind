import { beforeEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, type Pinia } from 'pinia'

import ChampionIcon from '@/components/game/ChampionIcon.vue'
import GameImage from '@/components/game/GameImage.vue'
import ItemIcon from '@/components/game/ItemIcon.vue'
import RankEmblem from '@/components/game/RankEmblem.vue'
import RuneIcon from '@/components/game/RuneIcon.vue'
import SpellIcon from '@/components/game/SpellIcon.vue'
import { COMMUNITY_DRAGON_IMAGES, formatRank } from '@/constants/tiers'
import { stripHtml } from '@/utils/text'
import { DD, loadedStaticData } from '../../fixtures/ddragon'

let pinia: Pinia
const options = () => ({ global: { plugins: [pinia] } })
const tooltip = (wrapper: ReturnType<typeof mount>) => wrapper.find('[role="tooltip"]')

describe('icônes du jeu', () => {
  beforeEach(async () => {
    pinia = (await loadedStaticData()).pinia
  })

  it('ChampionIcon : image, nom en info-bulle et niveau', () => {
    const wrapper = mount(ChampionIcon, { ...options(), props: { championId: 62, level: 15 } })

    const img = wrapper.find('img')
    expect(img.attributes('src')).toBe(`${DD}/cdn/16.21.1/img/champion/MonkeyKing.png`)
    expect(img.attributes('alt')).toBe('Wukong')
    expect(tooltip(wrapper).text()).toBe('Wukong')
    expect(wrapper.find('[data-testid="champion-level"]').text()).toBe('15')
  })

  it('ChampionIcon : champion absent de Data Dragon, repli sur le nom du match', () => {
    const wrapper = mount(ChampionIcon, {
      ...options(),
      props: { championId: 999, championName: 'NouveauChampion' },
    })

    expect(wrapper.find('img').attributes('src')).toBe(
      `${DD}/cdn/16.21.1/img/champion/NouveauChampion.png`,
    )
    expect(tooltip(wrapper).text()).toBe('NouveauChampion')
  })

  it('ItemIcon : nom et description en info-bulle', () => {
    const wrapper = mount(ItemIcon, { ...options(), props: { itemId: 3087 } })

    expect(wrapper.find('img').attributes('src')).toBe(`${DD}/cdn/16.21.1/img/item/3087.png`)
    expect(tooltip(wrapper).text()).toContain('Poignard de Statikk')
    expect(tooltip(wrapper).text()).toContain('Les déplacements génèrent des charges.')
  })

  it('ItemIcon : une case vide (0) est un emplacement sans info-bulle', () => {
    const wrapper = mount(ItemIcon, { ...options(), props: { itemId: 0, size: 24 } })

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.find('[data-testid="game-image-empty"]').attributes('style')).toContain('24px')
    expect(tooltip(wrapper).exists()).toBe(false)
  })

  it('SpellIcon : image et nom', () => {
    const wrapper = mount(SpellIcon, { ...options(), props: { spellId: 4 } })

    expect(wrapper.find('img').attributes('src')).toBe(
      `${DD}/cdn/16.21.1/img/spell/SummonerFlash.png`,
    )
    expect(tooltip(wrapper).text()).toBe('Saut éclair')
  })

  it('RuneIcon : keystone ou arbre, description sans HTML', () => {
    const keystone = mount(RuneIcon, { ...options(), props: { runeId: 8112 } })
    const tree = mount(RuneIcon, { ...options(), props: { runeId: 8100 } })

    expect(keystone.find('img').attributes('src')).toBe(
      `${DD}/cdn/img/perk-images/Styles/Domination/Electrocute/Electrocute.png`,
    )
    expect(tooltip(keystone).text()).toContain('Électrocution')
    expect(tooltip(keystone).text()).toContain('Toucher un champion avec 3 attaques')
    expect(tooltip(keystone).html()).not.toContain('<b>')
    expect(tree.find('img').attributes('alt')).toBe('Domination')
  })

  it('affiche des emplacements vides tant que Data Dragon n’est pas chargé', () => {
    const empty = { global: { plugins: [createPinia()] } }

    expect(
      mount(ChampionIcon, { ...empty, props: { championId: 429 } })
        .find('img')
        .exists(),
    ).toBe(false)
    expect(
      mount(ItemIcon, { ...empty, props: { itemId: 3087 } })
        .find('img')
        .exists(),
    ).toBe(false)
  })
})

describe('GameImage', () => {
  it('remplace une image introuvable par un emplacement vide', async () => {
    const wrapper = mount(GameImage, {
      props: { src: 'https://exemple.test/absente.png', alt: 'X' },
    })

    await wrapper.find('img').trigger('error')

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.find('[data-testid="game-image-empty"]').attributes('aria-label')).toBe('X')
  })

  it("réessaie quand l'URL change", async () => {
    const wrapper = mount(GameImage, { props: { src: 'https://exemple.test/a.png' } })
    await wrapper.find('img').trigger('error')

    await wrapper.setProps({ src: 'https://exemple.test/b.png' })

    expect(wrapper.find('img').attributes('src')).toBe('https://exemple.test/b.png')
  })
})

describe('RankEmblem', () => {
  it('affiche le blason du palier et le rang en texte alternatif', () => {
    const wrapper = mount(RankEmblem, { props: { tier: 'EMERALD', rank: 'IV' } })

    const img = wrapper.find('img')
    expect(img.attributes('src')).toBe(`${COMMUNITY_DRAGON_IMAGES}/ranked-mini-crests/emerald.svg`)
    expect(img.attributes('alt')).toBe('Émeraude IV')
  })

  it('utilise le grand emblème et affiche le libellé sans division pour Challenger', () => {
    const wrapper = mount(RankEmblem, {
      props: { tier: 'CHALLENGER', rank: 'I', variant: 'emblem', showLabel: true },
    })

    expect(wrapper.find('img').attributes('src')).toBe(
      `${COMMUNITY_DRAGON_IMAGES}/ranked-emblem/emblem-challenger.png`,
    )
    expect(wrapper.text()).toBe('Challenger')
  })

  it('affiche « Non classé » avec le blason correspondant (pas de grand emblème)', () => {
    const wrapper = mount(RankEmblem, { props: { tier: null, variant: 'emblem' } })

    expect(wrapper.find('img').attributes('src')).toBe(
      `${COMMUNITY_DRAGON_IMAGES}/ranked-mini-crests/unranked.svg`,
    )
    expect(wrapper.find('img').attributes('alt')).toBe('Non classé')
  })
})

describe('textes', () => {
  it('formatRank traduit les paliers et masque la division des paliers sans division', () => {
    expect(formatRank('GOLD', 'II')).toBe('Or II')
    expect(formatRank('GRANDMASTER', 'I')).toBe('Grand Maître')
    expect(formatRank('NOUVEAU', 'I')).toBe('NOUVEAU I')
    expect(formatRank(null)).toBe('Non classé')
  })

  it('stripHtml retire les balises des descriptions de Data Dragon', () => {
    expect(
      stripHtml(
        'Inflige <b>des dégâts</b>.<br><br>Puis <lol-uikit-tooltipped-keyword>soigne</lol-uikit-tooltipped-keyword>',
      ),
    ).toBe('Inflige des dégâts. Puis soigne')
  })
})
