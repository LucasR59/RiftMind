<script setup lang="ts">
import { computed } from 'vue'

import AppTooltip from '@/components/ui/AppTooltip.vue'
import GameImage from '@/components/game/GameImage.vue'
import { useStaticDataStore } from '@/stores/staticData'

/** Icône d'un champion, avec son nom en info-bulle et, en option, son niveau en fin de partie. */
const props = withDefaults(
  defineProps<{
    championId: number
    /** `championName` du match : sert si le champion manque dans Data Dragon (nouveau champion) */
    championName?: string
    size?: number
    round?: boolean
    /** Niveau affiché dans un coin */
    level?: number
  }>(),
  { championName: undefined, size: 40, round: false, level: undefined },
)

const staticData = useStaticDataStore()
const name = computed(
  () =>
    staticData.champion(props.championId)?.name ??
    props.championName ??
    `Champion ${props.championId}`,
)
const src = computed(() => staticData.championIconUrl(props.championId, props.championName))
</script>

<template>
  <AppTooltip :text="name">
    <span class="champion-icon">
      <GameImage :src="src" :alt="name" :size="size" :shape="round ? 'round' : 'rounded'" />
      <span v-if="level !== undefined" class="champion-icon__level" data-testid="champion-level">
        {{ level }}
      </span>
    </span>
  </AppTooltip>
</template>

<style scoped>
.champion-icon {
  position: relative;
  display: inline-flex;
}

.champion-icon__level {
  position: absolute;
  right: -4px;
  bottom: -4px;
  min-width: 1.15rem;
  padding: 0 0.2rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg);
  font-size: 0.65rem;
  font-weight: 700;
  line-height: 1.1rem;
  text-align: center;
}
</style>
