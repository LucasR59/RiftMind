<script setup lang="ts">
import { computed } from 'vue'
import { useBackendStatus } from '@/composables/useBackendStatus'

const { data, loading } = useBackendStatus()

const state = computed(() => {
  if (loading.value) return { key: 'loading', label: "Connexion à l'API…" }
  if (data.value?.status === 'ok') return { key: 'up', label: 'API connectée' }
  return { key: 'down', label: 'API injoignable' }
})
</script>

<template>
  <p class="status" :class="`status--${state.key}`" role="status" data-testid="backend-status">
    <span class="status__dot" aria-hidden="true" />
    {{ state.label }}
  </p>
</template>

<style scoped>
.status {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.35rem 0.85rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--color-text-muted);
  font-size: 0.85rem;
}

.status__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-text-muted);
}

.status--up .status__dot {
  background: var(--color-success);
  box-shadow: 0 0 8px var(--color-success);
}

.status--down .status__dot {
  background: var(--color-danger);
}

.status--loading .status__dot {
  animation: pulse 1s ease-in-out infinite alternate;
}

@keyframes pulse {
  to {
    opacity: 0.3;
  }
}
</style>
