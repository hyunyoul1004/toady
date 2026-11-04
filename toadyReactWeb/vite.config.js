import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
      api: path.resolve(__dirname, './src/api'),
      components: path.resolve(__dirname, './src/components'),
      features: path.resolve(__dirname, './src/features'),
      routes: path.resolve(__dirname, './src/routes'),
      utils: path.resolve(__dirname, './src/utils'),
      config: path.resolve(__dirname, './src/config'),
      // 레거시 호환 경로
      _actions: path.resolve(__dirname, './src/features/legacy-compat'),
      _constants: path.resolve(__dirname, './src/features/legacy-compat'),
      _reducers: path.resolve(__dirname, './src/features/legacy-compat'),
      _services: path.resolve(__dirname, './src/api'),
      _helpers: path.resolve(__dirname, './src/utils'),
      _utils: path.resolve(__dirname, './src/utils'),
      views: path.resolve(__dirname, './src/features')
    }
  },
  server: {
    host: '0.0.0.0', // 외부 접속 및 공유기 DDNS 접속 허용
    port: 3000,
    open: true,
    cors: true,
    allowedHosts: [
      'hyunyoul2.iptime.org',
      '.iptime.org',
      'localhost',
      '127.0.0.1'
    ],
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true
      },
      '/webApi': {
        target: 'http://localhost:8081',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: false
  }
});
