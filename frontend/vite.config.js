import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, path.resolve(process.cwd(), '..'), '')

  // 백엔드 주소 설정
  const apiTarget = env.VITE_API_TARGET || 'http://olm-backend:8050';

  console.log(`[Vite Config] API 연결 타겟: ${apiTarget}`);

  return {
    envDir: "../",
    plugins: [react()],
    base: mode === 'development' ? '/' : './',
    build: {
      outDir: 'dist',
      rollupOptions: {
        output: {
          entryFileNames: 'assets/chatbot.js',
          assetFileNames: (assetInfo) => {
            if (assetInfo.name && assetInfo.name.endsWith('.css')) {
              return 'assets/chatbot.css';
            }
            return 'assets/[name][extname]';
          },
        },
      },
    },
    server: {
      host: '0.0.0.0',
      port: 8060,
      strictPort: true,
      allowedHosts: [
        'sfolm.iptime.org',
        'localhost',
        '192.168.0.10',
        '121.167.219.226'
      ],
      cors: {
        origin: "*",
        methods: ["GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"],
        allowedHeaders: ["Content-Type", "Authorization", "X-Requested-With"],
      },
      watch: {
        usePolling: true,
      },
      hmr: {
        clientPort: 8060,
      },

      proxy: {
        '/api': {
          target: apiTarget,
          changeOrigin: true,
          secure: false,
        },
        '/docs': {
          target: 'http://olm-backend:8050',
          changeOrigin: true,
        },
        '/openapi.json': {
          target: 'http://olm-backend:8050',
          changeOrigin: true,
        },
        '/viewer': {
          target: 'http://olm-db-viewer:8501',
          changeOrigin: true,
          secure: false,
          ws: true,
        },
        '/healthz': {
            target: 'http://olm-db-viewer:8501',
            changeOrigin: true,
            secure: false, 
        },
        '/browser': {
          target: 'http://olm-neo4j:7474',
          changeOrigin: true,
          secure: false,
          ws: true,
          rewrite: (path) => path.replace(/^\/neo4j/, '')
        },
      },
    },
  }
})