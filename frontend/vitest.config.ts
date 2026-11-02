import { fileURLToPath } from 'node:url'
import { mergeConfig, defineConfig, configDefaults } from 'vitest/config'
import viteConfig from './vite.config.ts'

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      include: ['tests/unit/**/*.spec.ts'],
      exclude: [...configDefaults.exclude, 'tests/e2e/**'],
      restoreMocks: true,
      // Remet à zéro les appels des vi.fn() entre deux tests (vi.mock garde les mêmes fonctions)
      clearMocks: true,
      setupFiles: ['tests/setup.ts'],
      root: fileURLToPath(new URL('./', import.meta.url)),
    },
  }),
)
