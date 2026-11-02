import { onMounted, onScopeDispose } from 'vue'

import { useStaticDataStore } from '@/stores/staticData'

/** Fréquence à laquelle on regarde s'il est temps de revérifier (la vérification elle-même est limitée à 1 h). */
const POLL_MS = 15 * 60 * 1000

/**
 * Charge Data Dragon au démarrage, puis suit les changements de patch pendant que le site reste ouvert : quand
 * l'onglet redevient visible, et régulièrement s'il l'est, avec au plus une vérification par heure
 * ({@link useStaticDataStore} → `checkForUpdate`). À utiliser une fois, dans `App.vue`.
 */
export function useDataDragonUpdates() {
  const staticData = useStaticDataStore()
  void staticData.load()

  const check = () => {
    if (document.visibilityState === 'visible') {
      void staticData.checkForUpdate()
    }
  }

  onMounted(() => {
    document.addEventListener('visibilitychange', check)
  })
  const timer = setInterval(check, POLL_MS)

  onScopeDispose(() => {
    document.removeEventListener('visibilitychange', check)
    clearInterval(timer)
  })
}
