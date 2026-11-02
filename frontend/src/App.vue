<script setup lang="ts">
import { RouterView } from 'vue-router'
import AppHeader from '@/components/layout/AppHeader.vue'
import AppFooter from '@/components/layout/AppFooter.vue'
import { useStaticDataStore } from '@/stores/staticData'

// Données Data Dragon (champions, objets…) : chargées dès le démarrage, depuis le cache si la version n'a pas changé
void useStaticDataStore().load()
</script>

<template>
  <AppHeader />
  <main class="main">
    <RouterView />
  </main>
  <AppFooter />
</template>

<style scoped>
.main {
  flex: 1;
  width: 100%;
  max-width: var(--content-width);
  margin: 0 auto;
}
</style>
