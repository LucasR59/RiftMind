<script setup lang="ts">
import { computed } from 'vue'

import GameImage from '@/components/game/GameImage.vue'
import { formatRank, tierImageUrl } from '@/constants/tiers'

/** Emblème d'un palier classé (emblèmes officiels de Riot, servis par le backend). `tier` vide : « Non classé ». */
const props = withDefaults(
  defineProps<{
    tier?: string | null
    /** Division (I à IV), pour le texte alternatif et le libellé */
    rank?: string | null
    size?: number
    /** Affiche le rang en texte à côté de l'image */
    showLabel?: boolean
  }>(),
  { tier: null, rank: null, size: 48, showLabel: false },
)

const label = computed(() => formatRank(props.tier, props.rank))
const src = computed(() => tierImageUrl(props.tier))
</script>

<template>
  <span class="rank-emblem">
    <GameImage :src="src" :alt="showLabel ? '' : label" :size="size" shape="square" />
    <span v-if="showLabel" class="rank-emblem__label">{{ label }}</span>
  </span>
</template>

<style scoped>
.rank-emblem {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
}

.rank-emblem :deep(.game-image) {
  background: transparent;
  object-fit: contain;
}

.rank-emblem__label {
  font-weight: 700;
}
</style>
