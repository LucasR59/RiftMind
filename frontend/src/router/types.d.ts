import 'vue-router'
import type { RouteLocationNormalized } from 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    /** Titre affiché dans l'onglet du navigateur, fixe ou calculé à partir de la route (ex. le Riot ID) */
    title?: string | ((to: RouteLocationNormalized) => string)
  }
}
