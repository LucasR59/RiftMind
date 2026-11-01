import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    /** Titre affiché dans l'onglet du navigateur */
    title?: string
  }
}
