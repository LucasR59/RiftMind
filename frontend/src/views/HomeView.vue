<script setup lang="ts">
import BackendStatus from '@/components/BackendStatus.vue'
import logoUrl from '@/assets/logo.svg'
</script>

<template>
  <section class="hero">
    <div class="hero__glow" aria-hidden="true" />

    <img :src="logoUrl" alt="" class="hero__logo" width="96" height="96" />

    <h1 class="hero__title">RiftMind</h1>

    <p class="hero__tagline">Bienvenue sur RiftMind.</p>

    <BackendStatus />
  </section>
</template>

<style scoped>
.hero {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1.5rem;
  min-height: 70vh;
  padding: 4rem 1.5rem;
  text-align: center;
}

/* Taille bornée à la largeur d'écran : le flou déborde visuellement sans créer de scroll */
.hero__glow {
  position: absolute;
  top: 50%;
  left: 50%;
  width: min(520px, 90vw);
  height: min(520px, 90vw);
  translate: -50% -60%;
  border-radius: 50%;
  background: var(--gradient-brand);
  filter: blur(140px);
  opacity: 0.22;
  pointer-events: none;
}

.hero__logo {
  position: relative;
  filter: drop-shadow(0 0 24px rgb(139 92 246 / 45%));
}

.hero__title {
  position: relative;
  font-size: clamp(3rem, 9vw, 5.5rem);
  font-weight: 800;
  line-height: 1;
  letter-spacing: -0.03em;
  background: var(--gradient-brand);
  background-clip: text;
  color: transparent;
}

.hero__tagline {
  position: relative;
  max-width: 34rem;
  color: var(--color-text-muted);
  font-size: 1.15rem;
}
</style>
