<script setup lang="ts">
import { ref, watch } from 'vue'

import ErrorState from '@/components/ui/ErrorState.vue'
import LoadingSkeleton from '@/components/ui/LoadingSkeleton.vue'
import { getPlayerSummary } from '@/api/players'
import type { PlayerSummary, RoleShare } from '@/types/stats'
import { formatNumber, formatPercent } from '@/utils/format'

/** Résumé des 20 dernières parties (hors remakes) : bilan, winrate, KDA moyen et rôles joués. */
const props = defineProps<{ puuid: string; refreshKey?: number }>()

const ROLE_LABELS: Record<RoleShare['position'], string> = {
  TOP: 'Haut',
  JUNGLE: 'Jungle',
  MIDDLE: 'Milieu',
  BOTTOM: 'Bas',
  UTILITY: 'Support',
}

const summary = ref<PlayerSummary | null>(null)
const loading = ref(false)
const error = ref<unknown>(null)
let requestId = 0

async function load() {
  const id = ++requestId
  loading.value = true
  error.value = null
  try {
    const result = await getPlayerSummary(props.puuid)
    if (id === requestId) summary.value = result
  } catch (e) {
    if (id === requestId) error.value = e
  } finally {
    if (id === requestId) loading.value = false
  }
}

watch(() => [props.puuid, props.refreshKey], load, { immediate: true })

const roleShare = (role: RoleShare) =>
  `${Math.round((100 * role.games) / Math.max(1, summary.value?.games ?? 1))}%`
</script>

<template>
  <section class="summary" aria-labelledby="summary-title" :aria-busy="loading">
    <h2 id="summary-title" class="summary__title">
      {{ summary?.games ? `${summary.games} dernières parties` : 'Dernières parties' }}
    </h2>

    <LoadingSkeleton v-if="loading" :lines="4" />

    <ErrorState v-else-if="error" title="Résumé indisponible" retryable @retry="load" />

    <p v-else-if="!summary?.games" class="summary__empty" data-testid="summary-empty">
      Aucune partie récente.
    </p>

    <template v-else>
      <div class="summary__record">
        <p>
          <span class="summary__wins">{{ summary.wins }}V</span>
          <span class="summary__losses">{{ summary.losses }}D</span>
        </p>
        <p
          class="summary__winrate"
          :class="{ 'summary__winrate--good': summary.winrate >= 50 }"
          data-testid="summary-winrate"
        >
          {{ formatPercent(summary.winrate) }}
        </p>
      </div>
      <div
        class="summary__bar"
        role="img"
        :aria-label="`${formatPercent(summary.winrate)} de victoires`"
      >
        <span class="summary__bar-fill" :style="{ width: `${summary.winrate}%` }" />
      </div>

      <div class="summary__kda" data-testid="summary-kda">
        <p class="summary__kda-ratio">{{ formatNumber(summary.kda, 2, 2) }} KDA</p>
        <p class="summary__kda-detail">
          {{ formatNumber(summary.avgKills, 1) }} / {{ formatNumber(summary.avgDeaths, 1) }} /
          {{ formatNumber(summary.avgAssists, 1) }}
        </p>
      </div>

      <ul v-if="summary.roles.length" class="summary__roles" aria-label="Rôles joués">
        <li
          v-for="role in summary.roles"
          :key="role.position"
          class="summary__role"
          data-testid="summary-role"
        >
          <span class="summary__role-name">{{ ROLE_LABELS[role.position] }}</span>
          <span class="summary__role-bar" aria-hidden="true">
            <span class="summary__role-fill" :style="{ width: roleShare(role) }" />
          </span>
          <span class="summary__role-games">{{ role.games }}</span>
        </li>
      </ul>
    </template>
  </section>
</template>

<style scoped>
.summary {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  padding: 1rem 1.1rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface);
}

.summary__title {
  color: var(--color-text-muted);
  font-size: 0.8rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.summary__empty {
  color: var(--color-text-muted);
}

.summary__record {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  font-weight: 600;
}

.summary__wins {
  margin-right: 0.5rem;
  color: var(--color-accent);
}

.summary__losses {
  color: var(--color-danger);
}

.summary__winrate {
  font-size: 1.3rem;
  font-weight: 800;
}

.summary__winrate--good {
  color: var(--color-success);
}

.summary__bar,
.summary__role-bar {
  display: block;
  height: 6px;
  overflow: hidden;
  border-radius: 3px;
  background: color-mix(in srgb, var(--color-danger) 55%, transparent);
}

.summary__bar-fill {
  display: block;
  height: 100%;
  background: var(--color-accent);
}

.summary__kda {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.5rem;
}

.summary__kda-ratio {
  font-weight: 700;
}

.summary__kda-detail {
  color: var(--color-text-muted);
  font-size: 0.85rem;
}

.summary__roles {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  padding: 0;
  list-style: none;
}

.summary__role {
  display: grid;
  grid-template-columns: 4.5rem 1fr 1.5rem;
  gap: 0.5rem;
  align-items: center;
  font-size: 0.85rem;
}

.summary__role-bar {
  background: rgb(255 255 255 / 8%);
}

.summary__role-fill {
  display: block;
  height: 100%;
  border-radius: 3px;
  background: var(--color-primary);
}

.summary__role-games {
  color: var(--color-text-muted);
  text-align: right;
}
</style>
