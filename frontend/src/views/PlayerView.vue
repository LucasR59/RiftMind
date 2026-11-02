<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import ErrorState from '@/components/ui/ErrorState.vue'
import LoadingSkeleton from '@/components/ui/LoadingSkeleton.vue'
import PlayerHeader from '@/components/player/PlayerHeader.vue'
import RankCards from '@/components/player/RankCards.vue'
import MatchHistory from '@/components/match/MatchHistory.vue'
import ChampionStatsTable from '@/components/player/ChampionStatsTable.vue'
import { getPlayer } from '@/api/players'
import { findPlatform } from '@/constants/platforms'
import { useRecentSearches } from '@/composables/useRecentSearches'
import { playerRoute } from '@/router/links'
import type { Player } from '@/types/player'
import { formatRiotId, fromRiotIdSlug, toRiotIdSlug } from '@/utils/riotId'

/**
 * Profil d'un joueur. La route garantit une plateforme connue et un Riot ID complet (voir router/index.ts).
 * Charge le joueur, puis remplace l'URL par l'écriture exacte du Riot ID (`/euw1/joueur-euw` → `/euw1/Joueur-EUW`).
 */
const route = useRoute()
const router = useRouter()
const { add: addRecentSearch } = useRecentSearches()

const player = ref<Player | null>(null)
const loading = ref(false)
const error = ref<unknown>(null)
/** Incrémenté à chaque mise à jour du joueur : les blocs (parties, stats…) rechargent leurs données. */
const refreshKey = ref(0)

/** Onglet affiché ; l'onglet « Champions » n'est chargé qu'à sa première ouverture. */
const tab = ref<'matches' | 'champions'>('matches')
const championsOpened = ref(false)
function selectTab(name: 'matches' | 'champions') {
  tab.value = name
  if (name === 'champions') championsOpened.value = true
}

const platform = computed(() => findPlatform(String(route.params.platform)))
const riotId = computed(() => fromRiotIdSlug(String(route.params.riotId)))

/** Clé de la page, sans tenir compte de la casse : sert à ne pas recharger quand seule l'écriture change. */
const pageKey = (platformId: string, slug: string) => `${platformId}/${slug}`.toLowerCase()
let loadedKey: string | null = null
let requestId = 0

async function load() {
  if (!platform.value || !riotId.value) return
  const key = pageKey(platform.value.id, toRiotIdSlug(riotId.value))
  if (key === loadedKey && player.value) return

  const id = ++requestId
  loading.value = true
  error.value = null
  player.value = null
  try {
    const found = await getPlayer(platform.value.id, riotId.value.gameName, riotId.value.tagLine)
    if (id === requestId) show(found)
  } catch (e) {
    if (id === requestId) error.value = e
  } finally {
    if (id === requestId) loading.value = false
  }
}

/** Affiche le joueur, le retient dans les recherches récentes et met l'URL à son écriture exacte. */
function show(found: Player) {
  player.value = found
  loadedKey = pageKey(found.platform, toRiotIdSlug(found))
  addRecentSearch({ platform: found.platform, gameName: found.gameName, tagLine: found.tagLine })
  if (route.params.riotId !== toRiotIdSlug(found) || route.params.platform !== found.platform) {
    void router.replace({ ...playerRoute(found.platform, found), query: route.query })
  }
  document.title = `${formatRiotId(found)} · RiftMind`
}

function onRefreshed(updated: Player) {
  show(updated)
  refreshKey.value++
}

watch(() => [route.params.platform, route.params.riotId], load, { immediate: true })

const errorMessage = computed(() => (error.value instanceof Error ? error.value.message : null))
</script>

<template>
  <div class="player-page">
    <div v-if="loading" class="player-page__loading" aria-busy="true" aria-live="polite">
      <span class="visually-hidden">Chargement du profil…</span>
      <div class="player-page__skeleton-header">
        <LoadingSkeleton shape="circle" width="88px" />
        <LoadingSkeleton width="16rem" height="2rem" :lines="2" />
      </div>
    </div>

    <ErrorState
      v-else-if="error"
      title="Profil indisponible"
      :message="errorMessage"
      retryable
      @retry="load"
    />

    <template v-else-if="player">
      <PlayerHeader :player="player" @refreshed="onRefreshed" />
      <div class="player-page__layout">
        <aside class="player-page__aside">
          <RankCards :ranks="player.ranks" />
        </aside>
        <div class="player-page__main">
          <div class="player-page__tabs" role="tablist" aria-label="Contenu du profil">
            <button
              id="tab-matches"
              type="button"
              role="tab"
              class="player-page__tab"
              :aria-selected="tab === 'matches'"
              aria-controls="panel-matches"
              data-testid="tab-matches"
              @click="selectTab('matches')"
            >
              Parties
            </button>
            <button
              id="tab-champions"
              type="button"
              role="tab"
              class="player-page__tab"
              :aria-selected="tab === 'champions'"
              aria-controls="panel-champions"
              data-testid="tab-champions"
              @click="selectTab('champions')"
            >
              Champions
            </button>
          </div>

          <div
            v-show="tab === 'matches'"
            id="panel-matches"
            role="tabpanel"
            aria-labelledby="tab-matches"
          >
            <MatchHistory
              :puuid="player.puuid"
              :platform="player.platform"
              :refresh-key="refreshKey"
            />
          </div>
          <div
            v-if="championsOpened"
            v-show="tab === 'champions'"
            id="panel-champions"
            role="tabpanel"
            aria-labelledby="tab-champions"
          >
            <ChampionStatsTable :puuid="player.puuid" :refresh-key="refreshKey" />
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.player-page {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  padding: 2rem 1.5rem 3rem;
}

.player-page__layout {
  display: grid;
  gap: 1.25rem;
  align-items: start;
}

.player-page__aside,
.player-page__main {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  min-width: 0;
}

@media (min-width: 900px) {
  .player-page__layout {
    grid-template-columns: 300px minmax(0, 1fr);
  }
}

.player-page__tabs {
  display: flex;
  gap: 0.25rem;
  border-bottom: 1px solid var(--color-border);
}

.player-page__tab {
  margin-bottom: -1px;
  padding: 0.5rem 1rem;
  border: none;
  border-bottom: 2px solid transparent;
  background: none;
  color: var(--color-text-muted);
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}

.player-page__tab:hover {
  color: var(--color-text);
}

.player-page__tab[aria-selected='true'] {
  border-bottom-color: var(--color-primary);
  color: var(--color-text);
}

.player-page__skeleton-header {
  display: flex;
  align-items: center;
  gap: 1.25rem;
}
</style>
