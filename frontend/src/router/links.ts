import type { RouteLocationNamedRaw } from 'vue-router'

import { toRiotIdSlug, type RiotId } from '@/utils/riotId'

// Liens vers les pages du site. Dans un fichier à part pour que les composants puissent les importer sans importer
// le router (qui importe lui-même les vues).

/** Lien vers le profil d'un joueur, ex. `{ name: 'player', params: { platform: 'euw1', riotId: 'Nom-TAG' } }`. */
export function playerRoute(platform: string, riotId: RiotId): RouteLocationNamedRaw {
  return { name: 'player', params: { platform, riotId: toRiotIdSlug(riotId) } }
}
