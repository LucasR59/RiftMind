import { describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'

import RankHistoryChart from '@/components/player/RankHistoryChart.vue'
import type { RankHistory } from '@/types/player'
import { soloHistory } from '../../fixtures/api'

const mountChart = (history: RankHistory = soloHistory) =>
  mount(RankHistoryChart, { props: { history, queueLabel: 'Classée Solo/Duo' } })

/** Simule un survol à `ratio` de la largeur du graphique (jsdom ne calcule pas la mise en page). */
async function hoverAt(wrapper: ReturnType<typeof mountChart>, ratio: number) {
  const svg = wrapper.find('svg')
  svg.element.getBoundingClientRect = () =>
    ({ left: 0, top: 0, width: 280, height: 128, right: 280, bottom: 128, x: 0, y: 0 }) as DOMRect
  // jsdom n'a pas PointerEvent : un MouseEvent du même nom porte la position
  svg.element.dispatchEvent(new MouseEvent('pointermove', { clientX: ratio * 280, bubbles: true }))
  await nextTick()
}

describe('RankHistoryChart', () => {
  it('trace une courbe à travers tous les points', () => {
    const wrapper = mountChart()

    const line = wrapper.find('.rank-chart__line').attributes('d') ?? ''
    expect(line.match(/[ML]/g)).toHaveLength(3)
    expect(wrapper.find('.rank-chart__area').exists()).toBe(true)
  })

  it('gradue l’axe sur les divisions traversées', () => {
    const labels = mountChart()
      .findAll('.rank-chart__grid text')
      .map((t) => t.text())

    expect(labels).toEqual(['Or II', 'Or I', 'Platine IV', 'Platine III'])
  })

  it('repère et annote le peak', () => {
    const wrapper = mountChart()

    const marker = wrapper.find('[data-testid="rank-history-peak-marker"]')
    expect(marker.exists()).toBe(true)
    expect(marker.text()).toBe('Peak')
  })

  it('place les points selon les parties jouées : début du suivi, puis le nombre de parties', () => {
    const dates = mountChart()
      .findAll('.rank-chart__dates text')
      .map((t) => t.text())

    // Bilans 18, 24 et 28 parties : 10 parties jouées depuis le premier point
    expect(dates).toEqual(['1 oct.', '+10 parties'])
  })

  it('décrit la courbe et le peak dans le texte alternatif', () => {
    const label = mountChart().find('svg').attributes('aria-label')

    expect(label).toContain('Classée Solo/Duo')
    expect(label).toContain('de Or II · 45 LP')
    expect(label).toContain('à Or I · 80 LP')
    expect(label).toContain('en 10 parties')
    expect(label).toContain('Peak : Platine IV · 12 LP')
  })

  it('affiche le rang le plus proche au survol, puis le masque', async () => {
    const wrapper = mountChart()

    await hoverAt(wrapper, 0.62)
    const tooltip = wrapper.find('[data-testid="rank-history-tooltip"]')
    expect(tooltip.text()).toContain('Platine IV · 12 LP')
    expect(tooltip.text()).toContain('15V 9D')
    expect(wrapper.find('[data-testid="rank-history-change"]').text()).toBe('+167 LP en 6 parties')
    expect(wrapper.find('.rank-chart__cursor').exists()).toBe(true)

    await wrapper.find('svg').trigger('pointerleave')
    expect(wrapper.find('[data-testid="rank-history-tooltip"]').exists()).toBe(false)
  })

  it('se parcourt au clavier, du dernier rang vers les précédents', async () => {
    const wrapper = mountChart()
    const svg = wrapper.find('svg')

    await svg.trigger('focus')
    expect(wrapper.find('[data-testid="rank-history-tooltip"]').text()).toContain('Or I · 80 LP')

    await svg.trigger('keydown', { key: 'ArrowLeft' })
    expect(wrapper.find('[data-testid="rank-history-tooltip"]').text()).toContain('Platine IV')

    await svg.trigger('keydown', { key: 'Home' })
    expect(wrapper.find('[data-testid="rank-history-tooltip"]').text()).toContain('Or II · 45 LP')
    expect(wrapper.find('.rank-chart__live').text()).toContain('Or II · 45 LP')

    await svg.trigger('blur')
    expect(wrapper.find('[data-testid="rank-history-tooltip"]').exists()).toBe(false)
  })

  it('donne toutes les valeurs dans un tableau, du plus récent au plus ancien', () => {
    const rows = mountChart()
      .findAll('.rank-chart__table tbody tr')
      .map((row) => row.findAll('td').map((td) => td.text()))

    expect(rows.map((row) => row[1])).toEqual([
      'Or I · 80 LP',
      'Platine IV · 12 LP',
      'Or II · 45 LP',
    ])
    expect(rows.map((row) => row[2])).toEqual([
      '−32 LP en 4 parties',
      '+167 LP en 6 parties',
      'Début du suivi',
    ])
    expect(rows[0]?.[3]).toBe('16V 12D')
  })

  it("n'annote pas de peak plus ancien que les points affichés", () => {
    const wrapper = mountChart({
      ...soloHistory,
      peak: { ...soloHistory.peak, recordedAt: '2026-09-01T10:00:00Z', score: 2000 },
    })

    expect(wrapper.find('[data-testid="rank-history-peak-marker"]').exists()).toBe(false)
  })
})
