import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

function manualChunks(id: string) {
  const normalized = id.replace(/\\/g, '/')
  if (normalized.includes('/node_modules/')) {
    if (
      normalized.includes('/node_modules/@element-plus/icons-vue/')
      || normalized.includes('/node_modules/element-plus/')
    ) {
      return 'vendor-element-plus'
    }
    if (
      normalized.includes('/node_modules/@vue/')
      || normalized.includes('/node_modules/vue/')
      || normalized.includes('/node_modules/vue-router/')
      || normalized.includes('/node_modules/pinia/')
    ) {
      return 'vendor-vue'
    }
    return 'vendor'
  }
}

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': {
        changeOrigin: true,
        target: 'http://localhost:18080',
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks,
      },
      onwarn(warning, warn) {
        if (
          warning.code === 'INVALID_ANNOTATION' &&
          typeof warning.id === 'string' &&
          warning.id.includes('@vueuse/core')
        ) {
          return
        }

        warn(warning)
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
  },
})
