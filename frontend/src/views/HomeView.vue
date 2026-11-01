<script setup lang="ts">
import TheWelcome from '@/components/TheWelcome.vue'
import { useBackendStatus } from '@/composables/useBackendStatus'

const { data: backend, error, loading } = useBackendStatus()
</script>

<template>
  <main>
    <p v-if="loading">Connexion au backend…</p>
    <p v-else-if="backend">Backend : {{ backend.status }} ({{ backend.service }})</p>
    <p v-else>Backend injoignable : {{ error }}</p>
    <TheWelcome />
  </main>
</template>
