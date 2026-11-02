<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import ChampionIcon from '@/components/game/ChampionIcon.vue'
import ApiErrorState from '@/components/ui/ApiErrorState.vue'
import LoadingSkeleton from '@/components/ui/LoadingSkeleton.vue'
import { getChampionStats } from '@/api/players'
import { RANKED_FLEX_QUEUE, RANKED_SOLO_QUEUE } from '@/constants/queues'
import { useStaticDataStore } from '@/stores/staticData'
import type { ChampionStats } from '@/types/stats'
import { formatCompact, formatNumber, formatPercent } from '@/utils/format'

/**
 * Statistiques du joueur par champion (hors remakes), filtrables par file et triables par colonne. Par défaut : du
 * plus joué au moins joué, comme les renvoie l'API.
 */
const props = defineProps<{ puuid: string; refreshKey?: number }>()

type SortKey = 'champion' | 'games' | 'winrate' | 'kda' | 'csPerMinute' | 'avgDamage'

const FILTERS: { label: string; queue: number | undefined }[] = [
  { label: 'Toutes', queue: undefined },
  { label: 'Solo/Duo', queue: RANKED_SOLO_QUEUE },
  { label: 'Flexible', queue: RANKED_FLEX_QUEUE },
]
const COLUMNS: { key: SortKey; label: string }[] = [
  { key: 'champion', label: 'Champion' },
  { key: 'games', label: 'Parties' },
  { key: 'winrate', label: 'Winrate' },
  { key: 'kda', label: 'KDA' },
  { key: 'csPerMinute', label: 'CS/min' },
  { key: 'avgDamage', label: 'Dégâts' },
]

const staticData = useStaticDataStore()
const queue = ref<number | undefined>(undefined)
const stats = ref<ChampionStats[]>([])
const loading = ref(false)
const error = ref<unknown>(null)
const sortKey = ref<SortKey>('games')
const descending = ref(true)
let requestId = 0

async function load() {
  const id = ++requestId
  loading.value = true
  error.value = null
  try {
    const result = await getChampionStats(props.puuid, queue.value)
    if (id === requestId) stats.value = result
  } catch (e) {
    if (id === requestId) error.value = e
  } finally {
    if (id === requestId) loading.value = false
  }
}

watch(() => [props.puuid, props.refreshKey, queue.value], load, { immediate: true })

const championName = (row: ChampionStats) =>
  staticData.champion(row.championId)?.name ?? row.championName

function sortBy(key: SortKey) {
  if (sortKey.value === key) {
    descending.value = !descending.value
  } else {
    sortKey.value = key
    // Les noms se lisent de A à Z, les nombres du plus grand au plus petit
    descending.value = key !== 'champion'
  }
}

const sorted = computed(() => {
  const direction = descending.value ? -1 : 1
  return [...stats.value].sort((a, b) => {
    const diff =
      sortKey.value === 'champion'
        ? championName(a).localeCompare(championName(b), 'fr')
        : a[sortKey.value] - b[sortKey.value]
    // À égalité, le champion le plus joué d'abord
    return diff !== 0 ? direction * diff : b.games - a.games
  })
})

const ariaSort = (key: SortKey) =>
  sortKey.value === key ? (descending.value ? 'descending' : 'ascending') : 'none'
</script>

<template>
  <section class="champion-stats" aria-labelledby="champion-stats-title">
    <div class="champion-stats__header">
      <h2 id="champion-stats-title" class="champion-stats__title">Statistiques par champion</h2>
      <div class="champion-stats__filters" role="group" aria-label="File de jeu">
        <button
          v-for="filter in FILTERS"
          :key="filter.label"
          type="button"
          class="champion-stats__filter"
          :aria-pressed="queue === filter.queue"
          @click="queue = filter.queue"
        >
          {{ filter.label }}
        </button>
      </div>
    </div>

    <LoadingSkeleton v-if="loading" :lines="5" height="2rem" />

    <ApiErrorState
      v-else-if="error"
      :error="error"
      title="Statistiques indisponibles"
      @retry="load"
    />

    <p v-else-if="!stats.length" class="champion-stats__empty" data-testid="champion-stats-empty">
      Aucune partie enregistrée{{ queue === undefined ? '' : ' dans cette file' }}.
    </p>

    <div v-else class="champion-stats__scroll">
      <table class="champion-stats__table">
        <thead>
          <tr>
            <th
              v-for="column in COLUMNS"
              :key="column.key"
              scope="col"
              :aria-sort="ariaSort(column.key)"
            >
              <button
                type="button"
                class="champion-stats__sort"
                :data-testid="`sort-${column.key}`"
                @click="sortBy(column.key)"
              >
                {{ column.label }}
                <span class="champion-stats__arrow" aria-hidden="true">
                  {{ sortKey === column.key ? (descending ? '▼' : '▲') : '' }}
                </span>
              </button>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in sorted" :key="row.championId" data-testid="champion-row">
            <th scope="row" class="champion-stats__champion">
              <div class="champion-stats__champion-inner">
                <ChampionIcon
                  :champion-id="row.championId"
                  :champion-name="row.championName"
                  :size="28"
                />
                <span data-testid="champion-name">{{ championName(row) }}</span>
              </div>
            </th>
            <td>
              {{ row.games }}
              <small>{{ row.wins }}V {{ row.losses }}D</small>
            </td>
            <td :class="{ 'champion-stats__good': row.winrate >= 50 }">
              {{ formatPercent(row.winrate) }}
            </td>
            <td>
              {{ formatNumber(row.kda, 2, 2) }}
              <small>
                {{ formatNumber(row.avgKills, 1) }} / {{ formatNumber(row.avgDeaths, 1) }} /
                {{ formatNumber(row.avgAssists, 1) }}
              </small>
            </td>
            <td>{{ formatNumber(row.csPerMinute, 1) }}</td>
            <td>{{ formatCompact(row.avgDamage) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.champion-stats {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface);
}

.champion-stats__header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.champion-stats__title {
  font-size: 1rem;
  font-weight: 700;
}

.champion-stats__filters {
  display: inline-flex;
  padding: 0.2rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
}

.champion-stats__filter {
  padding: 0.3rem 0.75rem;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text-muted);
  font: inherit;
  font-size: 0.85rem;
  cursor: pointer;
}

.champion-stats__filter[aria-pressed='true'] {
  background: var(--color-primary);
  color: #fff;
  font-weight: 600;
}

.champion-stats__empty {
  padding: 1.5rem;
  color: var(--color-text-muted);
  text-align: center;
}

.champion-stats__scroll {
  overflow-x: auto;
}

.champion-stats__table {
  width: 100%;
  min-width: 520px;
  border-collapse: collapse;
  font-size: 0.85rem;
}

.champion-stats__table th,
.champion-stats__table td {
  padding: 0.4rem 0.5rem;
  text-align: center;
  white-space: nowrap;
}

.champion-stats__table thead th {
  border-bottom: 1px solid var(--color-border);
}

.champion-stats__table tbody tr + tr {
  border-top: 1px solid rgb(255 255 255 / 5%);
}

.champion-stats__table small {
  display: block;
  color: var(--color-text-muted);
  font-size: 0.72rem;
}

.champion-stats__sort {
  border: none;
  background: none;
  color: var(--color-text-muted);
  font: inherit;
  font-size: 0.72rem;
  font-weight: 600;
  text-transform: uppercase;
  cursor: pointer;
}

.champion-stats__sort:hover,
th[aria-sort='ascending'] .champion-stats__sort,
th[aria-sort='descending'] .champion-stats__sort {
  color: var(--color-text);
}

.champion-stats__arrow {
  display: inline-block;
  width: 0.8em;
  font-size: 0.65rem;
}

.champion-stats__table .champion-stats__champion {
  font-weight: 600;
  text-align: left;
}

.champion-stats__champion-inner {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.champion-stats__good {
  color: var(--color-success);
  font-weight: 700;
}
</style>
