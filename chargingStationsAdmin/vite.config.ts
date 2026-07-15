import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import path from 'node:path';

// https://vite.dev/config/
export default defineConfig(({ mode, command }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const gateway = env.VITE_GATEWAY_URL || 'http://localhost:8010';

  // Префиксы api-gateway (через nginx на сервере). В mock-режиме не используются;
  // в api-режиме браузер ходит на localhost, а Vite проксирует на шлюз (без CORS).
  const proxy: Record<string, { target: string; changeOrigin: boolean; ws?: boolean }> =
    Object.fromEntries(
      ['/user', '/contractor-admin', '/station-controll', '/payment', '/booking'].map((prefix) => [
        prefix,
        { target: gateway, changeOrigin: true },
      ]),
    );
  // Realtime-канал websocket-service (upgrade до ws).
  proxy['/websocket'] = { target: gateway, changeOrigin: true, ws: true };

  return {
    // В проде панель раздаётся nginx'ом по пути /console/ (см. nginx/conf.d/default.conf):
    // base влияет и на пути ассетов в index.html, и на клиентские маршруты (basepath роутера,
    // берётся из import.meta.env.BASE_URL). В dev оставляем корень для удобства.
    base: command === 'build' ? '/console/' : '/',
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: { '@': path.resolve(__dirname, './src') },
    },
    build: {
      rollupOptions: {
        output: {
          manualChunks: {
            charts: ['recharts'],
            router: ['@tanstack/react-router', '@tanstack/react-query'],
          },
        },
      },
    },
    server: {
      port: 5273,
      proxy,
    },
  };
});
