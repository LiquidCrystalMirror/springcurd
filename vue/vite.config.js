import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig(({ mode }) => {
  const API_TARGET = 'http://localhost:8081'

  return {
    plugins: [vue()],
    server: {
      port: 80,
      open: true,
      proxy: {
        '/auth': {
          target: API_TARGET,
          changeOrigin: true,
        },
        '/dashboard': {
          target: API_TARGET,
          changeOrigin: true,
        },
        '/orders': {
          target: API_TARGET,
          changeOrigin: true,
        },
        '/staff': {
          target: API_TARGET,
          changeOrigin: true,
        },
        '/replenish': {
          target: API_TARGET,
          changeOrigin: true,
        },
      },
    },
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src'),
      },
    },
  }
})
