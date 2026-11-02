import { onScopeDispose, ref } from 'vue'

/**
 * Heure courante (ms), mise à jour toutes les `intervalMs` : pour les comptes à rebours et les dates relatives
 * (« il y a 3 minutes »). Le minuteur s'arrête quand le composant est détruit.
 */
export function useNow(intervalMs = 1000) {
  const now = ref(Date.now())
  const timer = setInterval(() => {
    now.value = Date.now()
  }, intervalMs)
  onScopeDispose(() => clearInterval(timer))
  return now
}
