<script setup lang="ts">
import { computed } from 'vue'

import { DEFAULT_PLATFORM_ID, findPlatform, platformsByRegion } from '@/constants/platforms'

/**
 * Choix de la plateforme (serveur), groupé par région. `v-model` contient l'identifiant Riot (`euw1`).
 *
 * Fermé, il n'affiche que l'abréviation (« EUW ») ; la liste ouverte donne les noms complets. Le `<select>` natif
 * (accessible, liste du système sur mobile) est transparent et posé sur ce libellé court.
 */
const model = defineModel<string>({ default: DEFAULT_PLATFORM_ID })

withDefaults(
  defineProps<{
    /** Texte lu par les lecteurs d'écran (le sélecteur n'a pas de libellé visible) */
    label?: string
  }>(),
  { label: 'Région' },
)

const groups = platformsByRegion()
const selected = computed(() => findPlatform(model.value))
</script>

<template>
  <span class="platform-select" :title="selected?.label">
    <span class="platform-select__value" aria-hidden="true">
      {{ selected?.shortName ?? model }}
    </span>
    <span class="platform-select__chevron" aria-hidden="true">▾</span>
    <select
      v-model="model"
      class="platform-select__native"
      :aria-label="label"
      data-testid="platform-select"
    >
      <optgroup v-for="group in groups" :key="group.region" :label="group.label">
        <option v-for="platform in group.platforms" :key="platform.id" :value="platform.id">
          {{ platform.shortName }} — {{ platform.label }}
        </option>
      </optgroup>
    </select>
  </span>
</template>

<style scoped>
.platform-select {
  position: relative;
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  gap: 0.35rem;
  min-width: 5.25rem;
  padding: 0.6rem 0.75rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface);
  color: var(--color-text);
  font-weight: 700;
}

.platform-select:hover {
  border-color: var(--color-primary);
}

.platform-select:focus-within {
  outline: 2px solid var(--color-accent);
  outline-offset: 2px;
}

.platform-select__value {
  flex: 1;
}

.platform-select__chevron {
  color: var(--color-text-muted);
  font-size: 0.75rem;
}

/* Le vrai <select> couvre tout le libellé : clic, clavier et lecteurs d'écran passent par lui */
.platform-select__native {
  position: absolute;
  inset: 0;
  width: 100%;
  opacity: 0;
  font: inherit;
  cursor: pointer;
}

/* Liste ouverte (Windows, Linux) : garder le thème sombre lisible */
.platform-select__native optgroup,
.platform-select__native option {
  background: var(--color-surface);
  color: var(--color-text);
}
</style>
