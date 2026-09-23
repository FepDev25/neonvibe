/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';
import { VitePWA } from 'vite-plugin-pwa';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: [
        'favicon.svg', 'favicon-192.png', 'favicon-512.png',
        'apple-touch-icon.png',
      ],
      manifest: {
        name: 'NeonVibe',
        short_name: 'NeonVibe',
        description: 'Servidor de música personal self-hosted',
        lang: 'es',
        theme_color: '#0a0a12',
        background_color: '#0a0a12',
        display: 'standalone',
        start_url: '/',
        scope: '/',
        icons: [
          { src: '/icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
          { src: '/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
          { src: '/maskable-192.png', sizes: '192x192', type: 'image/png', purpose: 'maskable' },
          { src: '/maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,svg,png,ico,webmanifest}'],
        runtimeCaching: [
          {
            // Library/playlists/favorites API reads: serve stale while revalidating
            // so pages work offline with the last good data.
            urlPattern: ({ url, request }) =>
              request.method === 'GET' &&
              url.pathname.startsWith('/api/v1/') &&
              !/\/stream$|\/cover$/.test(url.pathname),
            handler: 'StaleWhileRevalidate',
            options: {
              cacheName: 'neonvibe-api',
              cacheableResponse: { statuses: [0, 200] },
              expiration: { maxEntries: 120, maxAgeSeconds: 60 * 60 * 24 * 7 },
            },
          },
          {
            // Cover images (URLs carry a ?token=, so ignore the query string).
            // StaleWhileRevalidate: offline shows the last cover and uploads
            // are revalidated (a fresh cover replaces the cached one).
            urlPattern: ({ url }) => /\/cover$/.test(url.pathname),
            handler: 'StaleWhileRevalidate',
            options: {
              cacheName: 'neonvibe-covers',
              cacheableResponse: { statuses: [0, 200] },
              expiration: { maxEntries: 500, maxAgeSeconds: 60 * 60 * 24 * 30 },
              matchOptions: { ignoreSearch: true },
            },
          },
        ],
      },
    }),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  build: {
    outDir: '../backend/src/main/resources/static',
    // The outDir lives outside the project root; empty it so stale hashed
    // assets don't accumulate inside the packaged JAR.
    emptyOutDir: true,
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: [
        'src/**/*.{test,spec}.{ts,tsx}',
        'src/test/**',
        'src/vite-env.d.ts',
        'src/main.tsx',
      ],
    },
  },
});
