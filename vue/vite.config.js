import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 80,
    open: true,
    proxy:{
      '/login':{
        target:'http://localhost:8080/users',
        changeOrigin:true,
        rewrite: (path) => path.replace(/^\/login/, '/login')
      },
      '/register':{
        target:'http://localhost:8080/users',
        changeOrigin:true,
        rewrite: (path) => path.replace(/^\/register/, '/register')
      },
      '/dashboard':{
        target:'http://localhost:8080',
        changeOrigin:true,
        rewrite: (path) => path.replace(/^\/dashboard/, '/dashboard')
      }
    }
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    }
  },

})
