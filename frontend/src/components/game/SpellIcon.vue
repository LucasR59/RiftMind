<script setup lang="ts">
import { computed } from 'vue'

import AppTooltip from '@/components/ui/AppTooltip.vue'
import GameImage from '@/components/game/GameImage.vue'
import { useStaticDataStore } from '@/stores/staticData'

/** Sort d'invocateur (Saut éclair, Téléportation…) avec son nom en info-bulle. */
const props = withDefaults(defineProps<{ spellId: number; size?: number }>(), { size: 22 })

const staticData = useStaticDataStore()
const name = computed(() => staticData.spell(props.spellId)?.name ?? `Sort ${props.spellId}`)
</script>

<template>
  <AppTooltip :text="name">
    <GameImage :src="staticData.spellIconUrl(spellId)" :alt="name" :size="size" />
  </AppTooltip>
</template>
