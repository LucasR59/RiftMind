import { onMounted, ref } from 'vue'
import { fetchPing } from '@/api/ping'
import type { PingResponse } from '@/types/api'

/** Interroge /api/ping au montage du composant et expose l'état de la connexion. */
export function useBackendStatus() {
  const data = ref<PingResponse | null>(null)
  const error = ref<string | null>(null)
  const loading = ref(true)

  onMounted(async () => {
    try {
      data.value = await fetchPing()
    } catch (e) {
      error.value = e instanceof Error ? e.message : String(e)
    } finally {
      loading.value = false
    }
  })

  return { data, error, loading }
}
