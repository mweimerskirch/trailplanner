import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  // MapLibre alone is ~1 MB minified; that is expected, not a sign of bloat.
  build: { chunkSizeWarningLimit: 1500 },
  server: {
    // During development the Spring Boot backend runs on 8081.
    proxy: { '/api': 'http://localhost:8081' },
  },
})
