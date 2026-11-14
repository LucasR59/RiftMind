<script setup lang="ts">
import { computed } from 'vue'

import RankEmblem from '@/components/game/RankEmblem.vue'
import RankHistoryChart from '@/components/player/RankHistoryChart.vue'
import { formatRank } from '@/constants/tiers'
import type { LeagueEntry, RankHistory } from '@/types/player'
import { formatNumber, formatPercent, formatShortDate, winrate } from '@/utils/format'
import { formatRankWithLp } from '@/utils/rankScale'

/**
 * Rang dans une file classée : emblème, rang, LP, victoires/défaites et winrate. Sans `entry` : « Non classé ».
 * En dessous : le peak et la courbe d'évolution du rang (`history`), dès qu'il y a deux rangs enregistrés.
 */
const props = defineProps<{
  /** Nom de la file, ex. « Classée Solo/Duo » */
  title: string
  entry?: LeagueEntry | null
  /** Évolution du rang dans cette file, `null` si aucun rang n'a encore été enregistré */
  history?: RankHistory | null
  /** L'évolution n'a pas pu être chargée */
  historyUnavailable?: boolean
}>()

const rate = computed(() => (props.entry ? winrate(props.entry.wins, props.entry.losses) : 0))
</script>

<template>
  <section class="rank-card" :class="{ 'rank-card--unranked': !entry }" data-testid="rank-card">
    <h2 class="rank-card__title">{{ title }}</h2>

    <div class="rank-card__body">
      <RankEmblem :tier="entry?.tier" :rank="entry?.rank" :size="64" />

      <div v-if="entry" class="rank-card__details">
        <p class="rank-card__rank" data-testid="rank-label">
          {{ formatRank(entry.tier, entry.rank) }}
        </p>
        <p class="rank-card__lp">{{ formatNumber(entry.leaguePoints) }} LP</p>
        <p class="rank-card__record">
          <span>{{ entry.wins }}V {{ entry.losses }}D</span>
          <span
            class="rank-card__winrate"
            :class="{ 'rank-card__winrate--good': rate >= 50 }"
            data-testid="rank-winrate"
          >
            {{ formatPercent(rate) }}
          </span>
        </p>
        <p v-if="entry.hotStreak" class="rank-card__streak">Série de victoires</p>
      </div>
      <p v-else class="rank-card__rank rank-card__rank--muted">Non classé</p>
    </div>

    <div v-if="history" class="rank-card__history" data-testid="rank-history">
      <p
        class="rank-card__peak"
        title="Meilleur rang enregistré par RiftMind"
        data-testid="rank-peak"
      >
        <span class="rank-card__peak-label">Peak</span>
        <span class="rank-card__peak-value">
          {{ formatRankWithLp(history.peak.tier, history.peak.rank, history.peak.leaguePoints) }}
        </span>
      </p>
      <RankHistoryChart v-if="history.points.length >= 2" :history="history" :queue-label="title" />
      <p v-else class="rank-card__history-hint" data-testid="rank-history-hint">
        Suivi depuis le {{ formatShortDate(history.points[0]?.recordedAt ?? '') }} : la courbe
        apparaîtra à la prochaine évolution du rang.
      </p>
    </div>
    <p v-else-if="historyUnavailable" class="rank-card__history-hint">
      Évolution du rang indisponible.
    </p>
  </section>
</template>

<style scoped>
.rank-card {
  padding: 1rem 1.1rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface);
}

.rank-card__title {
  margin-bottom: 0.6rem;
  color: var(--color-text-muted);
  font-size: 0.8rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.rank-card__body {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.rank-card--unranked .rank-card__body {
  opacity: 0.7;
}

.rank-card__details {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  min-width: 0;
}

.rank-card__rank {
  font-size: 1.15rem;
  font-weight: 800;
}

.rank-card__rank--muted {
  color: var(--color-text-muted);
}

.rank-card__lp {
  font-weight: 600;
}

.rank-card__record {
  display: flex;
  gap: 0.6rem;
  color: var(--color-text-muted);
  font-size: 0.85rem;
}

.rank-card__winrate {
  font-weight: 700;
}

.rank-card__winrate--good {
  color: var(--color-success);
}

.rank-card__history {
  margin-top: 0.85rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--color-border);
}

.rank-card__peak {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  margin-bottom: 0.4rem;
  font-size: 0.85rem;
}

.rank-card__peak-label {
  color: var(--color-text-muted);
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.rank-card__peak-value {
  font-weight: 700;
}

.rank-card__history-hint {
  margin-top: 0.5rem;
  color: var(--color-text-muted);
  font-size: 0.78rem;
}

.rank-card__streak {
  align-self: flex-start;
  margin-top: 0.2rem;
  padding: 0 0.45rem;
  border-radius: 6px;
  background: rgb(248 113 113 / 15%);
  color: var(--color-danger);
  font-size: 0.75rem;
  font-weight: 700;
}
</style>
