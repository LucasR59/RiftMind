<script setup lang="ts">
import { computed } from 'vue'

import AppTooltip from '@/components/ui/AppTooltip.vue'
import GameImage from '@/components/game/GameImage.vue'
import { useStaticDataStore } from '@/stores/staticData'
import { stripHtml } from '@/utils/text'

/**
 * Rune (keystone) ou arbre de runes, rond, avec son nom et sa description en info-bulle. 0 : emplacement vide.
 */
const props = withDefaults(defineProps<{ runeId: number; size?: number }>(), { size: 22 })

const staticData = useStaticDataStore()
const rune = computed(() => (props.runeId > 0 ? staticData.rune(props.runeId) : null))
const name = computed(() => rune.value?.name ?? `Rune ${props.runeId}`)
const description = computed(() => stripHtml(rune.value?.shortDesc ?? ''))
</script>

<template>
  <AppTooltip v-if="runeId > 0">
    <GameImage :src="staticData.runeIconUrl(runeId)" :alt="name" :size="size" shape="round" />
    <template #content>
      <strong class="rune-name">{{ name }}</strong>
      <span v-if="description" class="rune-text">{{ description }}</span>
    </template>
  </AppTooltip>
  <GameImage v-else :src="null" :size="size" shape="round" />
</template>

<style scoped>
.rune-name {
  display: block;
  color: var(--color-accent);
}

.rune-text {
  display: block;
  color: var(--color-text-muted);
}
</style>
