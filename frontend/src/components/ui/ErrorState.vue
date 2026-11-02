<script setup lang="ts">
/**
 * Message d'erreur dans une zone de la page (profil, historique…), avec un bouton « Réessayer » facultatif.
 * Le choix du texte selon l'erreur de l'API (joueur introuvable, Riot indisponible…) revient à l'appelant.
 */
withDefaults(
  defineProps<{
    title?: string
    message?: string | null
    /** Affiche le bouton de nouvel essai, qui émet `retry` */
    retryable?: boolean
    retryLabel?: string
    /** Désactive le bouton (ex. pendant un compte à rebours) */
    retryDisabled?: boolean
  }>(),
  {
    title: 'Une erreur est survenue',
    message: null,
    retryable: false,
    retryLabel: 'Réessayer',
    retryDisabled: false,
  },
)

const emit = defineEmits<{ retry: [] }>()
</script>

<template>
  <div class="error-state" role="alert" data-testid="error-state">
    <p class="error-state__title">{{ title }}</p>
    <p v-if="message" class="error-state__message">{{ message }}</p>
    <slot />
    <button
      v-if="retryable"
      type="button"
      class="error-state__retry"
      :disabled="retryDisabled"
      @click="emit('retry')"
    >
      {{ retryLabel }}
    </button>
  </div>
</template>

<style scoped>
.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.6rem;
  padding: 2rem 1.5rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface);
  text-align: center;
}

.error-state__title {
  font-size: 1.1rem;
  font-weight: 700;
}

.error-state__message {
  max-width: 36rem;
  color: var(--color-text-muted);
}

.error-state__retry {
  margin-top: 0.4rem;
  padding: 0.5rem 1.1rem;
  border: 1px solid var(--color-primary);
  border-radius: var(--radius);
  background: transparent;
  color: var(--color-text);
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}

.error-state__retry:hover:not(:disabled) {
  background: rgb(139 92 246 / 15%);
}

.error-state__retry:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
