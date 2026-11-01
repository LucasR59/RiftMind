<script setup lang="ts">
import { onMounted, ref } from 'vue'
import TheWelcome from '../components/TheWelcome.vue'
import { ping, type PingResponse } from '@/api/client'

const backend = ref<PingResponse | null>(null)
const error = ref<string | null>(null)

onMounted(async () => {
  try {
    backend.value = await ping()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
})
</script>

<template>
  <main>
    <p v-if="backend">Backend : {{ backend.status }} ({{ backend.service }})</p>
    <p v-else-if="error">Backend injoignable : {{ error }}</p>
    <p v-else>Connexion au backend…</p>
    <TheWelcome />
  </main>
</template>
