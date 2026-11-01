/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** URL de base de l'API (défaut : /api, proxifié par Vite en dev) */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
