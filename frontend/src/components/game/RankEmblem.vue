<script setup lang="ts">
import { computed } from 'vue'

import GameImage from '@/components/game/GameImage.vue'
import { formatRank, tierImageUrl } from '@/constants/tiers'

/**
 * Emblème d'un palier classé (CommunityDragon). `tier` vide : « Non classé ». `crest` (défaut) : petit blason SVG ;
 * `emblem` : grand emblème pour les cartes de rang.
 */
const props = withDefaults(
  defineProps<{
    tier?: string | null
    /** Division (I à IV), pour le texte alternatif et le libellé */
    rank?: string | null
    size?: number
    variant?: 'crest' | 'emblem'
    /** Affiche le rang en texte à côté de l'image */
    showLabel?: boolean
  }>(),
  { tier: null, rank: null, size: 48, variant: 'crest', showLabel: false },
)

const label = computed(() => formatRank(props.tier, props.rank))
const src = computed(() => tierImageUrl(props.tier, props.variant))
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
