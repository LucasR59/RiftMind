<script setup lang="ts">
import { computed } from 'vue'

import AppTooltip from '@/components/ui/AppTooltip.vue'
import GameImage from '@/components/game/GameImage.vue'
import { useStaticDataStore } from '@/stores/staticData'

/** Objet d'inventaire avec son nom et sa description en info-bulle. `itemId` 0 : case vide. */
const props = withDefaults(defineProps<{ itemId: number; size?: number }>(), { size: 28 })

const staticData = useStaticDataStore()
const item = computed(() => (props.itemId > 0 ? staticData.item(props.itemId) : null))
const name = computed(() => item.value?.name ?? `Objet ${props.itemId}`)
</script>

<template>
  <AppTooltip v-if="itemId > 0">
    <GameImage :src="staticData.itemIconUrl(itemId)" :alt="name" :size="size" />
    <template #content>
      <strong class="item-name">{{ name }}</strong>
      <span v-if="item?.plaintext" class="item-text">{{ item.plaintext }}</span>
    </template>
  </AppTooltip>
  <GameImage v-else :src="null" :size="size" />
</template>

<style scoped>
.item-name {
  display: block;
  color: var(--color-accent);
}

.item-text {
  display: block;
  color: var(--color-text-muted);
}
</style>
