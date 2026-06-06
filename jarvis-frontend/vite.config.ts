import type { Plugin } from 'vite'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

function preserveStandardBackdropFilter(): Plugin {
  return {
    name: 'preserve-standard-backdrop-filter',
    generateBundle(_, bundle) {
      for (const asset of Object.values(bundle)) {
        if (asset.type !== 'asset' || !asset.fileName.endsWith('.css') || typeof asset.source !== 'string') {
          continue
        }

        asset.source = asset.source.replace(
          /-webkit-backdrop-filter:([^;}]+);/g,
          'backdrop-filter:$1;-webkit-backdrop-filter:$1;',
        )
      }
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), preserveStandardBackdropFilter()],
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
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8084',
        changeOrigin: true,
        secure: false,
      }
    }
  }
})
