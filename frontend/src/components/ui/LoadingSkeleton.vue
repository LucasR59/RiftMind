<script setup lang="ts">
/**
 * Emplacement grisé animé, affiché pendant un chargement à la place du contenu. Purement décoratif : le conteneur
 * qui charge porte `aria-busy="true"`.
 */
withDefaults(
  defineProps<{
    /** Largeur CSS, ex. `100%`, `8rem` (diamètre pour `circle`) */
    width?: string
    /** Hauteur CSS, ex. `1rem`, `64px` (ignorée pour `circle`) */
    height?: string
    /** `text` : coins légers ; `circle` : rond (avatar, icône) ; `rect` : bloc (carte) */
    shape?: 'text' | 'rect' | 'circle'
    /** Nombre de lignes empilées ; la dernière est plus courte, comme un paragraphe */
    lines?: number
  }>(),
  { width: '100%', height: '1rem', shape: 'text', lines: 1 },
)
</script>

<template>
  <span class="skeleton-group" aria-hidden="true" data-testid="loading-skeleton">
    <span
      v-for="line in lines"
      :key="line"
      class="skeleton"
      :class="`skeleton--${shape}`"
      :style="{
        width: lines > 1 && line === lines ? '60%' : width,
        height: shape === 'circle' ? width : height,
      }"
    />
  </span>
</template>

<style scoped>
.skeleton-group {
  display: inline-flex;
  flex-direction: column;
  gap: 0.4rem;
  width: 100%;
}

.skeleton {
  display: block;
  max-width: 100%;
  background: linear-gradient(
    90deg,
    var(--color-surface) 25%,
    var(--color-border) 50%,
    var(--color-surface) 75%
  );
  background-size: 200% 100%;
  animation: shimmer 1.4s ease-in-out infinite;
}

.skeleton--text {
  border-radius: 4px;
}

.skeleton--rect {
  border-radius: var(--radius);
}

.skeleton--circle {
  flex-shrink: 0;
  border-radius: 50%;
}

@keyframes shimmer {
  from {
    background-position: 100% 0;
  }

  to {
    background-position: -100% 0;
  }
}
</style>
