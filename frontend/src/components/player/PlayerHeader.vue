<script setup lang="ts">
import { computed, ref } from 'vue'

import GameImage from '@/components/game/GameImage.vue'
import { ApiError } from '@/api/http'
import { refreshPlayer, REFRESH_COOLDOWN_SECONDS } from '@/api/players'
import { findPlatform } from '@/constants/platforms'
import { useNow } from '@/composables/useNow'
import { useStaticDataStore } from '@/stores/staticData'
import type { Player } from '@/types/player'
import { describeError } from '@/utils/errors'
import { formatCountdown, formatRelativeTime } from '@/utils/format'

/**
 * En-tête du profil : icône, niveau, Riot ID, région, date de la dernière mise à jour et bouton « Mettre à jour ».
 * Le bouton reste désactivé pendant le délai minimum entre deux mises à jour, avec un compte à rebours.
 */
const props = defineProps<{ player: Player }>()
const emit = defineEmits<{ refreshed: [player: Player] }>()

const staticData = useStaticDataStore()
const now = useNow(1000)

const platform = computed(() => findPlatform(props.player.platform))
const updatedAgo = computed(() => formatRelativeTime(props.player.lastRefreshedAt, now.value))

const refreshing = ref(false)
const refreshError = ref<string | null>(null)
/** Prochaine mise à jour possible selon le backend (réponse 429), en ms. */
const serverReadyAt = ref(0)

const readyAt = computed(() =>
  Math.max(
    Date.parse(props.player.lastRefreshedAt) + REFRESH_COOLDOWN_SECONDS * 1000,
    serverReadyAt.value,
  ),
)
const remainingSeconds = computed(() => Math.max(0, Math.ceil((readyAt.value - now.value) / 1000)))
const buttonLabel = computed(() => {
  if (refreshing.value) return 'Mise à jour…'
  if (remainingSeconds.value > 0)
    return `Mettre à jour (${formatCountdown(remainingSeconds.value)})`
  return 'Mettre à jour'
})

async function refresh() {
  refreshing.value = true
  refreshError.value = null
  try {
    emit('refreshed', await refreshPlayer(props.player.puuid))
  } catch (e) {
    if (e instanceof ApiError && e.code === 'REFRESH_COOLDOWN') {
      serverReadyAt.value = Date.now() + (e.retryAfterSeconds ?? REFRESH_COOLDOWN_SECONDS) * 1000
    } else {
      refreshError.value = `La mise à jour a échoué. ${describeError(e, 'Joueur').message}`
    }
  } finally {
    refreshing.value = false
  }
}
</script>

<template>
  <header class="profile-header">
    <div class="profile-header__icon">
      <GameImage
        :src="staticData.profileIconUrl(player.profileIconId)"
        alt=""
        :size="88"
        shape="rounded"
      />
      <span class="profile-header__level" data-testid="player-level">
        {{ player.summonerLevel }}
      </span>
    </div>

    <div class="profile-header__main">
      <h1 class="profile-header__name" data-testid="player-riot-id">
        {{ player.gameName }}<span class="profile-header__tag">#{{ player.tagLine }}</span>
      </h1>
      <p class="profile-header__meta">
        <span v-if="platform" class="profile-header__platform" :title="platform.label">
          {{ platform.shortName }}
        </span>
        <span data-testid="player-updated">Mis à jour {{ updatedAgo }}</span>
      </p>

      <div class="profile-header__actions">
        <button
          type="button"
          class="profile-header__refresh"
          :disabled="refreshing || remainingSeconds > 0"
          :aria-busy="refreshing"
          data-testid="refresh-button"
          @click="refresh"
        >
          {{ buttonLabel }}
        </button>
        <p
          v-if="refreshError"
          class="profile-header__error"
          role="alert"
          data-testid="refresh-error"
        >
          {{ refreshError }}
        </p>
      </div>
    </div>
  </header>
</template>

<style scoped>
.profile-header {
  display: flex;
  align-items: center;
  gap: 1.25rem;
}

.profile-header__icon {
  position: relative;
  flex-shrink: 0;
}

.profile-header__icon :deep(.game-image) {
  border: 2px solid var(--color-border);
}

.profile-header__level {
  position: absolute;
  bottom: -0.6rem;
  left: 50%;
  padding: 0 0.5rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg);
  font-size: 0.75rem;
  font-weight: 700;
  translate: -50% 0;
}

.profile-header__main {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  min-width: 0;
}

.profile-header__name {
  font-size: clamp(1.5rem, 5vw, 2rem);
  font-weight: 800;
  line-height: 1.15;
  overflow-wrap: anywhere;
}

.profile-header__tag {
  margin-left: 0.25rem;
  color: var(--color-text-muted);
  font-weight: 500;
}

.profile-header__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
  color: var(--color-text-muted);
  font-size: 0.9rem;
}

.profile-header__platform {
  padding: 0.05rem 0.5rem;
  border-radius: 6px;
  background: rgb(34 211 238 / 12%);
  color: var(--color-accent);
  font-size: 0.75rem;
  font-weight: 700;
}

.profile-header__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
  margin-top: 0.25rem;
}

.profile-header__refresh {
  padding: 0.5rem 1.1rem;
  border: none;
  border-radius: var(--radius);
  background: var(--gradient-brand);
  color: #fff;
  font: inherit;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  cursor: pointer;
}

.profile-header__refresh:disabled {
  background: var(--color-surface);
  color: var(--color-text-muted);
  cursor: not-allowed;
}

.profile-header__error {
  color: var(--color-danger);
  font-size: 0.85rem;
}
</style>
