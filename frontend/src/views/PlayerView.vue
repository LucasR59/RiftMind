<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import { findPlatform } from '@/constants/platforms'
import { formatRiotId, fromRiotIdSlug } from '@/utils/riotId'

// Profil d'un joueur : la route garantit déjà une plateforme connue et un Riot ID complet (voir router/index.ts).
// Le contenu (en-tête, rangs, historique…) arrive avec la phase 7.
const route = useRoute()

const platform = computed(() => findPlatform(String(route.params.platform)))
const riotId = computed(() => fromRiotIdSlug(String(route.params.riotId)))
</script>

<template>
  <section class="player">
    <h1 v-if="riotId" class="player__name" data-testid="player-riot-id">
      {{ formatRiotId(riotId) }}
    </h1>
    <p v-if="platform" class="player__platform">{{ platform.shortName }} — {{ platform.label }}</p>
  </section>
</template>

<style scoped>
.player {
  padding: 2rem 1.5rem;
}

.player__name {
  font-size: 2rem;
  font-weight: 800;
}

.player__platform {
  color: var(--color-text-muted);
}
</style>
