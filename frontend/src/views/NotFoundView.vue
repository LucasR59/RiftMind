<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<template>
  <section class="not-found">
    <p class="not-found__code">404</p>
    <h1>Page introuvable</h1>
    <p class="not-found__text">Cette page n'existe pas ou a été déplacée.</p>
    <RouterLink to="/" class="not-found__link">Retour à l'accueil</RouterLink>
  </section>
</template>

<style scoped>
.not-found {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  min-height: 60vh;
  padding: 3rem 1.5rem;
  text-align: center;
}

.not-found__code {
  font-size: 4rem;
  font-weight: 800;
  line-height: 1;
  background: var(--gradient-brand);
  background-clip: text;
  color: transparent;
}

.not-found__text {
  color: var(--color-text-muted);
}

.not-found__link {
  margin-top: 1rem;
  padding: 0.6rem 1.2rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  transition: border-color 0.2s;
}

.not-found__link:hover {
  border-color: var(--color-primary);
}
</style>
