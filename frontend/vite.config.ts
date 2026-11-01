import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// Cible du proxy /api : localhost en local, http://backend:8080 dans docker compose
const proxyTarget = process.env.VITE_PROXY_TARGET ?? 'http://localhost:8080'
// Le watcher natif ne voit pas les modifications d'un volume monté depuis Windows/macOS
const usePolling = process.env.VITE_USE_POLLING === 'true'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), vueDevTools()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    strictPort: true,
    watch: usePolling ? { usePolling: true, interval: 300 } : undefined,
    proxy: {
      // Redirige les appels /api vers le backend Spring Boot
      '/api': {
        target: proxyTarget,
        changeOrigin: true,
      },
    },
  },
})
