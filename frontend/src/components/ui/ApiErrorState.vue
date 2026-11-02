<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import ErrorState from '@/components/ui/ErrorState.vue'
import { useNow } from '@/composables/useNow'
import { describeError } from '@/utils/errors'
import { formatCountdown } from '@/utils/format'

/**
 * Erreur d'un appel à l'API, avec le message adapté (joueur introuvable, Riot indisponible, limite de requêtes…).
 * Pour une limite de requêtes, le bouton « Réessayer » reste désactivé pendant le délai indiqué par le backend.
 */
const props = withDefaults(
  defineProps<{
    error: unknown
    /** Ce qui était demandé, ex. « Joueur » → « Joueur introuvable » */
    subject?: string
    /** Remplace le titre (ex. « Historique indisponible »), le message reste celui de l'erreur */
    title?: string
  }>(),
  { subject: 'Ressource', title: undefined },
)

const emit = defineEmits<{ retry: [] }>()

const now = useNow(1000)
const description = computed(() => describeError(props.error, props.subject))
/** Instant (ms) à partir duquel on peut réessayer : fixé à l'arrivée de l'erreur. */
const retryAt = ref(0)
watch(
  () => props.error,
  () => {
    const seconds = description.value.retryAfterSeconds ?? 0
    // Sans délai : 0, et non « maintenant », qui arrondi à la seconde supérieure bloquerait le bouton 1 s
    retryAt.value = seconds > 0 ? Date.now() + seconds * 1000 : 0
  },
  { immediate: true },
)

const waitSeconds = computed(() => Math.max(0, Math.ceil((retryAt.value - now.value) / 1000)))
const retryLabel = computed(() =>
  waitSeconds.value > 0 ? `Réessayer dans ${formatCountdown(waitSeconds.value)}` : 'Réessayer',
)
const shownTitle = computed(() =>
  // Un titre propre au bloc garde l'information utile des erreurs « introuvable »
  props.title && description.value.kind !== 'not-found' ? props.title : description.value.title,
)
</script>

<template>
  <ErrorState
    :title="shownTitle"
    :message="description.message"
    :retryable="description.retryable"
    :retry-label="retryLabel"
    :retry-disabled="waitSeconds > 0"
    :data-error-kind="description.kind"
    @retry="emit('retry')"
  >
    <slot :kind="description.kind" />
  </ErrorState>
</template>
