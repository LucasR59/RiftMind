<script setup lang="ts">
import { ref, watch } from 'vue'

import ApiErrorState from '@/components/ui/ApiErrorState.vue'
import LoadingSkeleton from '@/components/ui/LoadingSkeleton.vue'
import MatchCard from '@/components/match/MatchCard.vue'
import { getPlayerMatches } from '@/api/matches'
import { useNow } from '@/composables/useNow'
import type { MatchSummary } from '@/types/match'

/**
 * Historique du joueur, de la partie la plus récente à la plus ancienne, par pages de {@link PAGE_SIZE}.
 * Rechargé depuis la première page quand `refreshKey` change (après « Mettre à jour »).
 */
const props = defineProps<{
  puuid: string
  /** Plateforme du profil, pour les liens vers les autres joueurs */
  platform: string
  refreshKey?: number
}>()

const PAGE_SIZE = 10

const now = useNow(60_000)
const matches = ref<MatchSummary[]>([])
const page = ref(0)
const hasMore = ref(false)
const loading = ref(false)
const loadingMore = ref(false)
const error = ref<unknown>(null)
const moreError = ref<unknown>(null)
let requestId = 0

async function loadFirstPage() {
  const id = ++requestId
  loading.value = true
  error.value = null
  moreError.value = null
  try {
    const result = await getPlayerMatches(props.puuid, { page: 0, size: PAGE_SIZE })
    if (id !== requestId) return
    matches.value = result.items
    page.value = 0
    hasMore.value = result.hasMore
  } catch (e) {
    if (id === requestId) error.value = e
  } finally {
    if (id === requestId) loading.value = false
  }
}

async function loadMore() {
  const id = requestId
  loadingMore.value = true
  moreError.value = null
  try {
    const result = await getPlayerMatches(props.puuid, { page: page.value + 1, size: PAGE_SIZE })
    if (id !== requestId) return
    const known = new Set(matches.value.map((m) => m.matchId))
    matches.value = [...matches.value, ...result.items.filter((m) => !known.has(m.matchId))]
    page.value = result.page
    hasMore.value = result.hasMore
  } catch (e) {
    if (id === requestId) moreError.value = e
  } finally {
    loadingMore.value = false
  }
}

watch(() => [props.puuid, props.refreshKey], loadFirstPage, { immediate: true })

defineExpose({ reload: loadFirstPage })
</script>

<template>
  <section class="history" aria-labelledby="history-title" :aria-busy="loading">
    <h2 id="history-title" class="visually-hidden">Historique des parties</h2>

    <div v-if="loading" class="history__list">
      <LoadingSkeleton v-for="n in 3" :key="n" shape="rect" height="96px" />
    </div>

    <ApiErrorState
      v-else-if="error"
      :error="error"
      title="Historique indisponible"
      @retry="loadFirstPage"
    />

    <p v-else-if="!matches.length" class="history__empty" data-testid="history-empty">
      Aucune partie enregistrée pour ce joueur. Utilisez « Mettre à jour » pour récupérer ses
      dernières parties.
    </p>

    <template v-else>
      <ol class="history__list">
        <li v-for="match in matches" :key="match.matchId">
          <MatchCard :match="match" :platform="platform" :now="now" />
        </li>
      </ol>

      <p v-if="moreError" class="history__more-error" role="alert">
        Impossible de charger plus de parties.
      </p>
      <button
        v-if="hasMore"
        type="button"
        class="history__more"
        :disabled="loadingMore"
        data-testid="load-more"
        @click="loadMore"
      >
        {{ loadingMore ? 'Chargement…' : 'Voir plus' }}
      </button>
    </template>
  </section>
</template>

<style scoped>
.history {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.history__list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 0;
  list-style: none;
}

.history__empty {
  padding: 2rem 1rem;
  border: 1px dashed var(--color-border);
  border-radius: var(--radius);
  color: var(--color-text-muted);
  text-align: center;
}

.history__more {
  align-self: center;
  padding: 0.55rem 1.5rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}

.history__more:hover:not(:disabled) {
  border-color: var(--color-primary);
}

.history__more:disabled {
  color: var(--color-text-muted);
  cursor: wait;
}

.history__more-error {
  color: var(--color-danger);
  font-size: 0.9rem;
  text-align: center;
}
</style>
