<script setup lang="ts">
import { RouterLink } from 'vue-router'

import { findPlatform } from '@/constants/platforms'
import { useRecentSearches } from '@/composables/useRecentSearches'
import { playerRoute } from '@/router/links'
import { formatRiotId } from '@/utils/riotId'

/** Joueurs consultés récemment (sur cet appareil), en liens vers leur profil. */
const { items, remove, clear } = useRecentSearches()
</script>

<template>
  <section v-if="items.length" class="recent" aria-labelledby="recent-title">
    <div class="recent__header">
      <h2 id="recent-title" class="recent__title">Recherches récentes</h2>
      <button type="button" class="recent__clear" @click="clear">Effacer</button>
    </div>
    <ul class="recent__list">
      <li
        v-for="search in items"
        :key="`${search.platform}/${search.gameName}#${search.tagLine}`"
        class="recent__item"
        data-testid="recent-search"
      >
        <RouterLink :to="playerRoute(search.platform, search)" class="recent__link">
          <span class="recent__platform">{{ findPlatform(search.platform)?.shortName }}</span>
          {{ formatRiotId(search) }}
        </RouterLink>
        <button
          type="button"
          class="recent__remove"
          :aria-label="`Retirer ${formatRiotId(search)} des recherches récentes`"
          @click="remove(search)"
        >
          ×
        </button>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.recent {
  width: 100%;
  max-width: 40rem;
}

.recent__header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 0.5rem;
}

.recent__title {
  color: var(--color-text-muted);
  font-size: 0.85rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.recent__clear,
.recent__remove {
  border: none;
  background: none;
  color: var(--color-text-muted);
  font: inherit;
  cursor: pointer;
}

.recent__clear:hover,
.recent__remove:hover {
  color: var(--color-text);
}

.recent__clear {
  font-size: 0.85rem;
}

.recent__list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  padding: 0;
  list-style: none;
}

.recent__item {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-surface);
}

.recent__item:hover {
  border-color: var(--color-primary);
}

.recent__link {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.3rem 0.2rem 0.3rem 0.75rem;
  font-size: 0.9rem;
}

.recent__platform {
  color: var(--color-accent);
  font-size: 0.75rem;
  font-weight: 700;
}

.recent__remove {
  padding: 0 0.65rem 0 0.35rem;
  font-size: 1.1rem;
  line-height: 1;
}
</style>
