<script setup lang="ts">
import { ref, useId } from 'vue'
import { useRouter } from 'vue-router'

import PlatformSelect from '@/components/PlatformSelect.vue'
import { DEFAULT_PLATFORM_ID } from '@/constants/platforms'
import { useRecentSearches } from '@/composables/useRecentSearches'
import { playerRoute } from '@/router/links'
import { parseRiotIdInput } from '@/utils/riotId'

/**
 * Recherche d'un joueur : région + « Nom#TAG ». Vérifie le format avant d'ouvrir le profil ; c'est la page du
 * profil qui dira si le joueur existe (et l'ajoutera aux recherches récentes). La région proposée est celle de la
 * dernière recherche.
 */
withDefaults(defineProps<{ compact?: boolean }>(), { compact: false })

const router = useRouter()
const { items: recent } = useRecentSearches()

const platform = ref(recent.value[0]?.platform ?? DEFAULT_PLATFORM_ID)
const query = ref('')
const error = ref<string | null>(null)
const errorId = useId()

async function submit() {
  const parsed = parseRiotIdInput(query.value)
  if (!parsed.ok) {
    error.value = parsed.error
    return
  }
  error.value = null
  query.value = ''
  await router.push(playerRoute(platform.value, parsed.riotId))
}
</script>

<template>
  <form
    class="search"
    :class="{ 'search--compact': compact }"
    role="search"
    novalidate
    @submit.prevent="submit"
  >
    <div class="search__bar">
      <PlatformSelect v-model="platform" class="search__platform" />
      <input
        v-model="query"
        type="search"
        class="search__input"
        placeholder="Nom#TAG"
        aria-label="Riot ID du joueur (Nom#TAG)"
        autocomplete="off"
        spellcheck="false"
        enterkeyhint="search"
        :aria-invalid="error ? 'true' : undefined"
        :aria-describedby="error ? errorId : undefined"
        data-testid="search-input"
        @input="error = null"
      />
      <button type="submit" class="search__submit">Rechercher</button>
    </div>
    <p v-if="error" :id="errorId" class="search__error" role="alert" data-testid="search-error">
      {{ error }}
    </p>
  </form>
</template>

<style scoped>
.search {
  width: 100%;
  max-width: 40rem;
}

.search__bar {
  display: flex;
  gap: 0.5rem;
  padding: 0.4rem;
  border: 1px solid var(--color-border);
  border-radius: calc(var(--radius) + 4px);
  background: rgb(20 24 41 / 85%);
  box-shadow: 0 12px 40px rgb(0 0 0 / 35%);
}

.search__bar:focus-within {
  border-color: var(--color-primary);
}

.search__platform {
  flex-shrink: 0;
}

.search__input {
  flex: 1;
  min-width: 0;
  padding: 0.6rem 0.75rem;
  border: none;
  background: transparent;
  color: var(--color-text);
  font: inherit;
  font-size: 1.05rem;
}

.search__input:focus-visible {
  outline: none;
}

.search__submit {
  flex-shrink: 0;
  padding: 0.6rem 1.2rem;
  border: none;
  border-radius: var(--radius);
  background: var(--gradient-brand);
  color: #fff;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}

.search__submit:hover {
  filter: brightness(1.1);
}

.search__error {
  margin-top: 0.5rem;
  color: var(--color-danger);
  font-size: 0.9rem;
  text-align: left;
}

.search--compact .search__bar {
  padding: 0.25rem;
  box-shadow: none;
}

.search--compact .search__input {
  padding: 0.4rem 0.5rem;
  font-size: 0.95rem;
}

.search--compact .search__submit {
  padding: 0.4rem 0.8rem;
}

@media (max-width: 480px) {
  .search:not(.search--compact) .search__bar {
    flex-wrap: wrap;
  }

  .search:not(.search--compact) .search__input {
    flex-basis: calc(100% - 7.5rem);
  }

  .search:not(.search--compact) .search__submit {
    flex-basis: 100%;
  }
}
</style>
