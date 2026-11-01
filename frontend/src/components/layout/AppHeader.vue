<script setup lang="ts">
import { RouterLink } from 'vue-router'
import logoUrl from '@/assets/logo.svg'
</script>

<template>
  <header class="header">
    <div class="header__inner">
      <RouterLink to="/" class="brand" aria-label="RiftMind - accueil">
        <img :src="logoUrl" alt="" width="28" height="28" />
        <span>RiftMind</span>
      </RouterLink>

      <nav class="nav" aria-label="Navigation principale">
        <RouterLink to="/">Accueil</RouterLink>
      </nav>
    </div>
  </header>
</template>

<style scoped>
.header {
  position: sticky;
  top: 0;
  z-index: 10;
  border-bottom: 1px solid var(--color-border);
  background: rgb(11 13 23 / 80%);
  backdrop-filter: blur(10px);
}

.header__inner {
  max-width: var(--content-width);
  margin: 0 auto;
  padding: 0.9rem 1.5rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.brand {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  font-weight: 700;
  font-size: 1.15rem;
  letter-spacing: 0.02em;
}

.nav {
  display: flex;
  gap: 1.5rem;
  font-size: 0.95rem;
}

.nav a {
  color: var(--color-text-muted);
  transition: color 0.2s;
}

.nav a:hover,
.nav a.router-link-exact-active {
  color: var(--color-text);
}
</style>
