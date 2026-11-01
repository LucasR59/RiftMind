import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
      meta: { title: 'Accueil' },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      // Chargée à la demande (chunk séparé)
      component: () => import('@/views/NotFoundView.vue'),
      meta: { title: 'Page introuvable' },
    },
  ],
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · RiftMind` : 'RiftMind'
})

export default router
