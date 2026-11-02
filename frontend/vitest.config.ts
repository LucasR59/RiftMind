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
      setupFiles: ['tests/setup.ts'],
      root: fileURLToPath(new URL('./', import.meta.url)),
    },
  }),
)
