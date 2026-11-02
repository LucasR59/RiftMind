<script setup lang="ts">
import { computed, useId, useSlots } from 'vue'

/**
 * Info-bulle affichée au survol ou au focus de son contenu (sans JavaScript de positionnement). Texte simple avec
 * `text`, ou contenu riche avec le slot `content`. Sans texte ni slot, seul le contenu est rendu.
 */
const props = withDefaults(
  defineProps<{
    text?: string | null
    placement?: 'top' | 'bottom'
  }>(),
  { text: null, placement: 'top' },
)

const slots = useSlots()
const id = useId()
const hasBubble = computed(() => Boolean(props.text) || Boolean(slots.content))
</script>

<template>
  <span class="tooltip" :aria-describedby="hasBubble ? id : undefined">
    <slot />
    <span
      v-if="hasBubble"
      :id="id"
      role="tooltip"
      class="tooltip__bubble"
      :class="`tooltip__bubble--${placement}`"
    >
      <slot name="content">{{ text }}</slot>
    </span>
  </span>
</template>

<style scoped>
.tooltip {
  position: relative;
  display: inline-flex;
}

.tooltip__bubble {
  position: absolute;
  left: 50%;
  z-index: 20;
  width: max-content;
  max-width: 16rem;
  padding: 0.45rem 0.65rem;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #0f1222;
  box-shadow: 0 8px 24px rgb(0 0 0 / 45%);
  color: var(--color-text);
  font-size: 0.8rem;
  font-weight: 400;
  line-height: 1.4;
  text-align: left;
  white-space: normal;
  translate: -50% 0;
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transition:
    opacity 0.15s,
    visibility 0.15s;
}

.tooltip__bubble--top {
  bottom: calc(100% + 6px);
}

.tooltip__bubble--bottom {
  top: calc(100% + 6px);
}

.tooltip:hover > .tooltip__bubble,
.tooltip:focus-within > .tooltip__bubble {
  opacity: 1;
  visibility: visible;
}
</style>
