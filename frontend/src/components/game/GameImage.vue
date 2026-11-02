<script setup lang="ts">
import { ref, watch } from 'vue'

/**
 * Image du jeu (champion, objet, sort…) de taille fixe. Sans URL (données pas encore chargées, case vide) ou si
 * l'image n'existe pas (Data Dragon en retard après un patch), affiche un emplacement vide de même taille.
 */
const props = withDefaults(
  defineProps<{
    src: string | null
    /** Texte alternatif ; vide pour une image décorative */
    alt?: string
    /** Côté en pixels */
    size?: number
    shape?: 'square' | 'rounded' | 'round'
  }>(),
  { alt: '', size: 32, shape: 'rounded' },
)

const failed = ref(false)
watch(
  () => props.src,
  () => {
    failed.value = false
  },
)
</script>

<template>
  <img
    v-if="src && !failed"
    :src="src"
    :alt="alt"
    :width="size"
    :height="size"
    loading="lazy"
    class="game-image"
    :class="`game-image--${shape}`"
    @error="failed = true"
  />
  <span
    v-else
    class="game-image game-image--empty"
    :class="`game-image--${shape}`"
    :style="{ width: `${size}px`, height: `${size}px` }"
    :role="alt ? 'img' : undefined"
    :aria-label="alt || undefined"
    data-testid="game-image-empty"
  />
</template>

<style scoped>
.game-image {
  display: block;
  flex-shrink: 0;
  object-fit: cover;
  background: var(--color-surface);
}

.game-image--rounded {
  border-radius: 6px;
}

.game-image--round {
  border-radius: 50%;
}

.game-image--empty {
  border: 1px solid var(--color-border);
  background: rgb(255 255 255 / 3%);
}
</style>
