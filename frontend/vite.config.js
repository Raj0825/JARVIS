import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        configure: (proxy) => {
          proxy.on('error', (err) => {
            if (err.code === 'ECONNABORTED' || err.code === 'ECONNRESET' || err.code === 'ECONNREFUSED') return;
            console.warn('[vite] api proxy error:', err.message);
          });
        }
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
        changeOrigin: true,
        configure: (proxy) => {
          proxy.on('error', (err) => {
            if (err.code === 'ECONNABORTED' || err.code === 'ECONNRESET' || err.code === 'ECONNREFUSED') return;
            console.warn('[vite] ws proxy error:', err.message);
          });
        }
      }
    }
  }
})
