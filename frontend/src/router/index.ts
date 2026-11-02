import {
  createRouter,
  createWebHistory,
  type RouteLocationNormalized,
  type RouteRecordRaw,
  type RouterHistory,
} from 'vue-router'
import HomeView from '@/views/HomeView.vue'
import { findPlatform } from '@/constants/platforms'
import { formatRiotId, fromRiotIdSlug } from '@/utils/riotId'

export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'home',
    component: HomeView,
    meta: { title: 'Accueil' },
  },
  {
    // Profil d'un joueur, ex. /euw1/Joueur%20Test-EUW pour « Joueur Test#EUW » sur EUW
    path: '/:platform/:riotId',
    name: 'player',
    component: () => import('@/views/PlayerView.vue'),
    meta: {
      title: (to: RouteLocationNormalized) => {
        const riotId = fromRiotIdSlug(String(to.params.riotId))
        return riotId ? formatRiotId(riotId) : 'Joueur'
      },
    },
    beforeEnter: (to) => {
      const platform = findPlatform(String(to.params.platform))
      if (!platform || !fromRiotIdSlug(String(to.params.riotId))) {
        // Plateforme inconnue ou Riot ID sans tag : page introuvable, en gardant l'URL saisie
        return {
          name: 'not-found',
          params: { pathMatch: to.path.slice(1).split('/') },
          query: to.query,
          hash: to.hash,
          replace: true,
        }
      }
      // Abréviation ou majuscules (/EUW/…) : redirige vers l'URL canonique (/euw1/…)
      if (to.params.platform !== platform.id) {
        return {
          name: 'player',
          params: { ...to.params, platform: platform.id },
          query: to.query,
          hash: to.hash,
          replace: true,
        }
      }
    },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    // Chargée à la demande (chunk séparé)
    component: () => import('@/views/NotFoundView.vue'),
    meta: { title: 'Page introuvable' },
  },
]

export { playerRoute } from './links'

/** Crée le router. `history` est remplaçable pour les tests (`createMemoryHistory()`). */
export function createAppRouter(
  history: RouterHistory = createWebHistory(import.meta.env.BASE_URL),
) {
  const router = createRouter({ history, routes })

  router.afterEach((to) => {
    const title = typeof to.meta.title === 'function' ? to.meta.title(to) : to.meta.title
    document.title = title ? `${title} · RiftMind` : 'RiftMind'
  })

  return router
}

export default createAppRouter()
