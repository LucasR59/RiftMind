<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import RankCard from '@/components/player/RankCard.vue'
import { getRankHistory } from '@/api/players'
import type { LeagueEntry, RankedQueue, RankHistory } from '@/types/player'

/**
 * Les deux files classées, toujours affichées : SoloQ puis Flex, « Non classé » si le joueur n'y joue pas. Avec
 * `puuid`, chaque carte montre aussi le peak et l'évolution du rang, rechargés quand `refreshKey` change (bouton
 * « Mettre à jour ») ; pendant le rechargement, l'évolution précédente reste affichée.
 */
const props = defineProps<{ ranks: LeagueEntry[]; puuid?: string; refreshKey?: number }>()

const QUEUES: { queueType: RankedQueue; title: string }[] = [
  { queueType: 'RANKED_SOLO_5x5', title: 'Classée Solo/Duo' },
  { queueType: 'RANKED_FLEX_SR', title: 'Classée Flexible' },
]

const history = ref<RankHistory[]>([])
const historyFailed = ref(false)
let requestId = 0

async function loadHistory() {
  const puuid = props.puuid
  if (!puuid) return
  const id = ++requestId
  try {
    const result = await getRankHistory(puuid)
    if (id === requestId) {
      history.value = result
      historyFailed.value = false
    }
  } catch {
    // L'évolution est un complément : la carte de rang reste affichée, avec un message discret
    if (id === requestId) historyFailed.value = true
  }
}

watch(
  () => props.puuid,
  () => {
    history.value = []
    historyFailed.value = false
  },
)
watch(() => [props.puuid, props.refreshKey], loadHistory, { immediate: true })

const cards = computed(() =>
  QUEUES.map((queue) => ({
    ...queue,
    entry: props.ranks.find((rank) => rank.queueType === queue.queueType) ?? null,
    history: history.value.find((h) => h.queueType === queue.queueType) ?? null,
  })),
)
</script>

<template>
  <div class="rank-cards">
    <RankCard
      v-for="card in cards"
      :key="card.queueType"
      :title="card.title"
      :entry="card.entry"
      :history="card.history"
      :history-unavailable="historyFailed && !!card.entry"
    />
  </div>
</template>

<style scoped>
.rank-cards {
  display: grid;
  gap: 0.75rem;
  align-items: start;
}

@media (min-width: 560px) and (max-width: 899px) {
  .rank-cards {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
