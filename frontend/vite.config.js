import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, path.resolve(process.cwd(), '..'), '')

  // 백엔드 주소 설정
  const apiTarget = env.VITE_API_TARGET || 'http://localhost:8095';

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
          target: apiTarget,
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/docs/, '/swagger-ui/index.html'),
        },
        '/swagger-ui': {
          target: apiTarget,
          changeOrigin: true,
        },
        '/v3/api-docs': {
          target: apiTarget,
          changeOrigin: true,
        },
        '/openapi.json': {
          target: apiTarget,
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