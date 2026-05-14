import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return

          if (id.includes('react') || id.includes('scheduler')) {
            return 'react-vendor'
          }

          if (
            id.includes('markmap-lib') ||
            id.includes('markmap-view')
          ) {
            return 'mindmap-vendor'
          }

          if (id.includes('/d3') || id.includes('\\d3')) {
            return 'd3-vendor'
          }

          if (id.includes('lucide-react')) {
            return 'ui-vendor'
          }

          if (id.includes('axios')) {
            return 'network-vendor'
          }
        },
      },
    },
  },
  server: {
    port: 5175,
    proxy: {
      '/api': {
        target: 'http://localhost:8084',
        changeOrigin: true,
        secure: false,
      }
    }
  }
})
