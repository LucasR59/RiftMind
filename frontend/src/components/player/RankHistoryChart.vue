<script setup lang="ts">
import { computed, ref, useId } from 'vue'

import type { RankHistory, RankPoint } from '@/types/player'
import { formatDateTime, formatShortDate, formatShortDateTime } from '@/utils/format'
import {
  formatGames,
  formatPointChange,
  formatRankWithLp,
  gamesPlayed,
  pointChanges,
  scoreLabel,
  scoreScale,
} from '@/utils/rankScale'

/**
 * Courbe d'évolution du rang dans une file (au moins 2 points), avec le peak.
 *
 * - Axe vertical : le score (LP cumulés), gradué sur les divisions ou les paliers.
 * - Axe horizontal : les parties jouées depuis le début du suivi (d'après le bilan de chaque point), pour lire la
 *   courbe partie par partie. Sans aucune partie entre les points (baisse d'inactivité seulement), ils sont espacés
 *   régulièrement.
 * - Survol, ou focus puis flèches ← → : un trait vertical se cale sur le rang le plus proche et une info-bulle
 *   l'affiche, avec la variation depuis le point précédent. Toutes les valeurs restent lisibles sans survol dans
 *   « Voir les valeurs ».
 * - Une seule série : pas de légende, le titre de la carte dit ce qui est tracé.
 */
const props = defineProps<{
  history: RankHistory
  /** Nom de la file, pour le texte alternatif, ex. « Classée Solo/Duo » */
  queueLabel: string
}>()

// Repère du dessin (unités du viewBox ; le SVG s'adapte à la largeur de la carte)
const WIDTH = 280
const HEIGHT = 128
const PLOT = { left: 56, right: WIDTH - 10, top: 16, bottom: HEIGHT - 22 }

const tableId = useId()
const points = computed(() => props.history.points)
const scale = computed(() => scoreScale(points.value.map((point) => point.score)))

/** Parties jouées depuis le premier point, pour chaque point. */
const games = computed(() => gamesPlayed(points.value))
const totalGames = computed(() => games.value[games.value.length - 1] ?? 0)
const changes = computed(() => pointChanges(points.value))

/** Position horizontale du point n° `i` : ses parties jouées, ou son rang dans la liste sans aucune partie. */
const x = (i: number) => {
  const width = PLOT.right - PLOT.left
  if (totalGames.value > 0) return PLOT.left + ((games.value[i] ?? 0) / totalGames.value) * width
  return PLOT.left + (i / Math.max(1, points.value.length - 1)) * width
}
const y = (score: number) =>
  PLOT.bottom -
  ((score - scale.value.min) / (scale.value.max - scale.value.min)) * (PLOT.bottom - PLOT.top)

const coords = computed(() => points.value.map((point, i) => ({ x: x(i), y: y(point.score) })))
const linePath = computed(() =>
  coords.value.map((c, i) => `${i === 0 ? 'M' : 'L'}${c.x.toFixed(1)},${c.y.toFixed(1)}`).join(''),
)
const areaPath = computed(() => {
  const first = coords.value[0]
  const last = coords.value[coords.value.length - 1]
  if (!first || !last) return ''
  return `${linePath.value}L${last.x.toFixed(1)},${PLOT.bottom}L${first.x.toFixed(1)},${PLOT.bottom}Z`
})

const ticks = computed(() =>
  scale.value.ticks.map((score) => ({
    score,
    y: y(score),
    label: scoreLabel(score, scale.value.step),
  })),
)

const last = computed(() => coords.value[coords.value.length - 1] ?? { x: 0, y: 0 })

/** Index du peak dans les points affichés (il peut être plus ancien que le premier point renvoyé). */
const peakIndex = computed(() =>
  points.value.findIndex(
    (point) =>
      point.recordedAt === props.history.peak.recordedAt &&
      point.score === props.history.peak.score,
  ),
)
const peakCoords = computed(() =>
  peakIndex.value >= 0 ? coords.value[peakIndex.value] : undefined,
)
const peakAnchor = computed(() => {
  const px = peakCoords.value?.x ?? 0
  if (px > PLOT.right - 14) return 'end'
  if (px < PLOT.left + 14) return 'start'
  return 'middle'
})

// --- Survol et clavier

const active = ref<number | null>(null)
const activePoint = computed<RankPoint | null>(() =>
  active.value === null ? null : (points.value[active.value] ?? null),
)
const activeCoords = computed(() =>
  active.value === null ? null : (coords.value[active.value] ?? null),
)

function nearestIndex(viewBoxX: number): number {
  let best = 0
  coords.value.forEach((c, i) => {
    if (Math.abs(c.x - viewBoxX) < Math.abs((coords.value[best]?.x ?? 0) - viewBoxX)) best = i
  })
  return best
}

function onPointerMove(event: PointerEvent) {
  const svg = event.currentTarget as SVGSVGElement
  const box = svg.getBoundingClientRect()
  if (box.width === 0) return
  active.value = nearestIndex(((event.clientX - box.left) / box.width) * WIDTH)
}

function onFocus() {
  active.value ??= points.value.length - 1
}

function onKeydown(event: KeyboardEvent) {
  const lastIndex = points.value.length - 1
  const current = active.value ?? lastIndex
  const next = {
    ArrowLeft: Math.max(0, current - 1),
    ArrowRight: Math.min(lastIndex, current + 1),
    Home: 0,
    End: lastIndex,
  }[event.key]
  if (next === undefined) return
  event.preventDefault()
  active.value = next
}

/** Info-bulle à gauche du point dans la moitié droite, à droite sinon : elle ne sort jamais de la carte. */
const tooltipStyle = computed(() => {
  const c = activeCoords.value
  if (!c) return {}
  const left = (c.x / WIDTH) * 100
  return {
    left: `${left}%`,
    top: `${(c.y / HEIGHT) * 100}%`,
    transform: `translate(${left > 50 ? 'calc(-100% - 10px)' : '10px'}, -50%)`,
  }
})

const describe = (point: RankPoint) => formatRankWithLp(point.tier, point.rank, point.leaguePoints)

const summary = computed(() => {
  const first = points.value[0]
  const lastPoint = points.value[points.value.length - 1]
  if (!first || !lastPoint) return ''
  return (
    `Évolution du rang en ${props.queueLabel} : de ${describe(first)} le ${formatDateTime(first.recordedAt)} ` +
    `à ${describe(lastPoint)} le ${formatDateTime(lastPoint.recordedAt)}, ` +
    `${totalGames.value > 0 ? `en ${formatGames(totalGames.value)}` : 'sans partie jouée'}. ` +
    `Peak : ${describe(props.history.peak)}.`
  )
})

/** Lignes du tableau, du plus récent au plus ancien, avec la variation depuis le point précédent. */
const rowsNewestFirst = computed(() =>
  points.value
    .map((point, i) => ({ point, change: formatPointChange(changes.value[i] ?? null) }))
    .reverse(),
)
const activeChange = computed(() =>
  active.value === null ? '' : formatPointChange(changes.value[active.value] ?? null),
)
</script>

<template>
  <div class="rank-chart" data-testid="rank-history-chart">
    <div class="rank-chart__plot">
      <svg
        :viewBox="`0 0 ${WIDTH} ${HEIGHT}`"
        role="img"
        :aria-label="summary"
        :aria-describedby="tableId"
        tabindex="0"
        class="rank-chart__svg"
        @pointermove="onPointerMove"
        @pointerleave="active = null"
        @focus="onFocus"
        @blur="active = null"
        @keydown="onKeydown"
      >
        <!-- Graduations : traits fins et pleins, libellés à gauche -->
        <g class="rank-chart__grid">
          <template v-for="tick in ticks" :key="tick.score">
            <line :x1="PLOT.left" :x2="PLOT.right" :y1="tick.y" :y2="tick.y" />
            <text :x="PLOT.left - 6" :y="tick.y" text-anchor="end" dominant-baseline="middle">
              {{ tick.label }}
            </text>
          </template>
        </g>

        <!-- Axe horizontal : début du suivi, puis le nombre de parties jouées depuis -->
        <g class="rank-chart__dates">
          <text :x="PLOT.left" :y="HEIGHT - 6" text-anchor="start">
            {{ formatShortDate(points[0]?.recordedAt ?? '') }}
          </text>
          <text :x="PLOT.right" :y="HEIGHT - 6" text-anchor="end" data-testid="rank-history-games">
            {{
              totalGames > 0
                ? `+${formatGames(totalGames)}`
                : formatShortDate(points[points.length - 1]?.recordedAt ?? '')
            }}
          </text>
        </g>

        <path class="rank-chart__area" :d="areaPath" />
        <path class="rank-chart__line" :d="linePath" />

        <!-- Peak : repère et libellé (le seul point annoté, avec la fin de la courbe) -->
        <g v-if="peakCoords" data-testid="rank-history-peak-marker">
          <circle class="rank-chart__dot" :cx="peakCoords.x" :cy="peakCoords.y" r="4" />
          <text
            class="rank-chart__peak-label"
            :x="peakCoords.x"
            :y="Math.max(10, peakCoords.y - 9)"
            :text-anchor="peakAnchor"
          >
            Peak
          </text>
        </g>
        <circle class="rank-chart__dot" :cx="last.x" :cy="last.y" r="4" />

        <!-- Survol : trait vertical et point actif -->
        <g v-if="activeCoords" class="rank-chart__cursor">
          <line :x1="activeCoords.x" :x2="activeCoords.x" :y1="PLOT.top" :y2="PLOT.bottom" />
          <circle class="rank-chart__dot" :cx="activeCoords.x" :cy="activeCoords.y" r="5" />
        </g>
      </svg>

      <div
        v-if="activePoint"
        class="rank-chart__tooltip"
        :style="tooltipStyle"
        data-testid="rank-history-tooltip"
      >
        <strong>{{ describe(activePoint) }}</strong>
        <span class="rank-chart__change" data-testid="rank-history-change">{{ activeChange }}</span>
        <span>{{ formatDateTime(activePoint.recordedAt) }}</span>
        <span class="rank-chart__muted">{{ activePoint.wins }}V {{ activePoint.losses }}D</span>
      </div>
    </div>

    <p class="rank-chart__live" aria-live="polite">
      {{
        activePoint
          ? `${describe(activePoint)}, ${activeChange}, ${formatDateTime(activePoint.recordedAt)}`
          : ''
      }}
    </p>

    <details :id="tableId" class="rank-chart__table">
      <summary>Voir les valeurs</summary>
      <div class="rank-chart__table-scroll">
        <table>
          <thead>
            <tr>
              <th scope="col">Date</th>
              <th scope="col">Rang</th>
              <th scope="col">Variation</th>
              <th scope="col">Bilan</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rowsNewestFirst" :key="row.point.recordedAt + row.point.score">
              <td>{{ formatShortDateTime(row.point.recordedAt) }}</td>
              <td>{{ describe(row.point) }}</td>
              <td>{{ row.change }}</td>
              <td>{{ row.point.wins }}V {{ row.point.losses }}D</td>
            </tr>
          </tbody>
        </table>
      </div>
    </details>
  </div>
</template>

<style scoped>
.rank-chart__plot {
  position: relative;
}

.rank-chart__svg {
  display: block;
  width: 100%;
  height: auto;
  overflow: visible;
  border-radius: 6px;
  touch-action: pan-y;
}

.rank-chart__svg:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.rank-chart__grid line {
  stroke: var(--color-border);
  stroke-width: 1;
  shape-rendering: crispEdges;
}

.rank-chart__grid text,
.rank-chart__dates text {
  fill: var(--color-text-muted);
  font-size: 9.5px;
  font-variant-numeric: tabular-nums;
}

.rank-chart__area {
  fill: var(--color-primary);
  opacity: 0.1;
}

.rank-chart__line {
  fill: none;
  stroke: var(--color-primary);
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

/* Point : couleur de la série, anneau de 2 px couleur du fond pour rester lisible sur la courbe */
.rank-chart__dot {
  fill: var(--color-primary);
  stroke: var(--color-surface);
  stroke-width: 2;
}

.rank-chart__peak-label {
  fill: var(--color-text);
  font-size: 9.5px;
  font-weight: 700;
}

.rank-chart__cursor line {
  stroke: var(--color-text-muted);
  stroke-width: 1;
  shape-rendering: crispEdges;
}

.rank-chart__tooltip {
  position: absolute;
  z-index: 2;
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  padding: 0.4rem 0.55rem;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg);
  box-shadow: 0 6px 20px rgb(0 0 0 / 35%);
  color: var(--color-text-muted);
  font-size: 0.75rem;
  white-space: nowrap;
  pointer-events: none;
}

.rank-chart__tooltip strong {
  color: var(--color-text);
  font-size: 0.85rem;
}

.rank-chart__muted {
  color: var(--color-text-muted);
}

.rank-chart__live {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}

.rank-chart__table {
  margin-top: 0.35rem;
  color: var(--color-text-muted);
  font-size: 0.75rem;
}

.rank-chart__table summary {
  width: fit-content;
  cursor: pointer;
}

.rank-chart__table-scroll {
  overflow-x: auto;
}

.rank-chart__change {
  color: var(--color-text);
}

.rank-chart__table table {
  width: 100%;
  margin-top: 0.4rem;
  border-collapse: collapse;
  font-variant-numeric: tabular-nums;
}

.rank-chart__table th,
.rank-chart__table td {
  padding: 0.2rem 0.25rem;
  border-bottom: 1px solid var(--color-border);
  text-align: left;
  white-space: nowrap;
}

.rank-chart__table th {
  font-weight: 600;
}

.rank-chart__table td:nth-child(2) {
  color: var(--color-text);
}
</style>
