<script setup lang="ts">
import { computed } from 'vue'

import RankCard from '@/components/player/RankCard.vue'
import type { LeagueEntry, RankedQueue } from '@/types/player'

/** Les deux files classées, toujours affichées : SoloQ puis Flex, « Non classé » si le joueur n'y joue pas. */
const props = defineProps<{ ranks: LeagueEntry[] }>()

const QUEUES: { queueType: RankedQueue; title: string }[] = [
  { queueType: 'RANKED_SOLO_5x5', title: 'Classée Solo/Duo' },
  { queueType: 'RANKED_FLEX_SR', title: 'Classée Flexible' },
]

const cards = computed(() =>
  QUEUES.map((queue) => ({
    ...queue,
    entry: props.ranks.find((rank) => rank.queueType === queue.queueType) ?? null,
  })),
)
</script>

<template>
  <div class="rank-cards">
    <RankCard v-for="card in cards" :key="card.queueType" :title="card.title" :entry="card.entry" />
  </div>
</template>

<style scoped>
.rank-cards {
  display: grid;
  gap: 0.75rem;
}

@media (min-width: 560px) and (max-width: 899px) {
  .rank-cards {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
