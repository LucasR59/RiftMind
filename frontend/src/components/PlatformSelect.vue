<script setup lang="ts">
import { platformsByRegion, DEFAULT_PLATFORM_ID } from '@/constants/platforms'

/**
 * Choix de la plateforme (serveur), groupé par région. `v-model` contient l'identifiant Riot (`euw1`).
 * Affiche l'abréviation dans la liste fermée et le nom complet dans les options.
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
</script>

<template>
  <select v-model="model" class="platform-select" :aria-label="label" data-testid="platform-select">
    <optgroup v-for="group in groups" :key="group.region" :label="group.label">
      <option
        v-for="platform in group.platforms"
        :key="platform.id"
        :value="platform.id"
        :title="platform.label"
      >
        {{ platform.shortName }} — {{ platform.label }}
      </option>
    </optgroup>
  </select>
</template>

<style scoped>
.platform-select {
  min-width: 6.5rem;
  padding: 0.6rem 0.75rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}

.platform-select:hover {
  border-color: var(--color-primary);
}
</style>
