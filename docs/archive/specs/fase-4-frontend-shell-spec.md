# Fase 4 — Frontend Shell y Tema: Especificación

> **Fase:** 4 de 11
> **Tipo:** Frontend puro (setup y shell)
> **Dependencias:** Fase 0 (backend base). No requiere APIs backend funcionales.
> **Objetivo:** Que la app exista en el navegador con el tema neón, routing y layout mobile-first, listo para que la Fase 5 conecte la librería.

---

## 1. Alcance

Esta fase crea la base de UI del frontend de NeonVibe:

- Proyecto `frontend/` con Vite + React 18 + TypeScript (strict).
- Tailwind CSS con paleta neón y sistema de temas `dark` (default) / `light`.
- React Router v6 con las rutas raíz de la app.
- Layout mobile-first: header superior, bottom nav fija, contenido scrolleable.
- Stores Zustand de placeholder: `themeStore`, `authStore`, `playerStore`.
- PWA mínima: `manifest.json` + `vite-plugin-pwa` (service worker de precache).
- Componentes UI reutilizables con estética neón.

**Fuera de alcance:** integración con APIs reales (Fase 5), reproductor funcional (Fase 7), carátulas/letras (Fase 8), offline/descargas (Fase 9).

---

## 2. Stack y Versiones

| Capa | Tecnología | Notas |
|---|---|---|
| Runtime | Node 20 LTS (o superior, p. ej. v22) | Se valida contra Node 22.23.2 local |
| Package manager | pnpm | Instalado global en `~/.npm-global/bin` |
| Bundler | Vite | Servidor dev + build |
| Framework | React 18 | Funcional components + hooks |
| Lenguaje | TypeScript | modo strict |
| Estilos | Tailwind CSS | dark/light via clase `dark` |
| Estado global | Zustand | con persistencia en `localStorage` |
| Query/Cache | TanStack Query | presente para Fase 5 |
| HTTP | Axios | cliente para Fase 5 |
| Audio (futuro) | Howler.js | placeholder en stores, tipo incluido |
| Iconos | lucide-react | iconos UI |
| Router | React Router v6 | rutas raíz |
| PWA | vite-plugin-pwa | registerType autoUpdate |

### Versiones estables de referencia (agosto 2026)

- `vite` ^7.x, `@vitejs/plugin-react` ^5.x
- `tailwindcss` ^4.x con `@tailwindcss/vite` (o ^3.4 con PostCSS clásico — se elige una)
- `react` / `react-dom` ^18.3.x
- `react-router-dom` ^6.x
- `zustand` ^5.x
- `@tanstack/react-query` ^5.x
- `axios` ^1.x
- `howler` ^2.2.x + `@types/howler`
- `lucide-react` ^0.4xx
- `vite-plugin-pwa` ^0.2x
- `typescript` ^5.x

> **Decisión Tailwind:** Se intenta Tailwind v4 (`@tailwindcss/vite`) por ser la versión actual estable de 2026 y no requerir `postcss`/`autoprefixer` separados. Si la paleta neón con CSS variables y el `dark` class-mode resultan problemáticos, se documenta el fallback a Tailwind v3.4 + PostCSS clásico.

---

## 3. Setup de Node / pnpm

- Node 20 LTS como objetivo; compatible con Node 22 (el local).
- pnpm: uso global ya instalado. Se añade `packageManager` en `package.json`:
  ```json
  "packageManager": "pnpm@9.15.9"
  ```
- `.npmrc` al nivel de `frontend/` con:
  ```ini
  strict-peer-dependencies=false
  auto-install-peers=true
  ```

---

## 4. Configuración de Vite (`vite.config.ts`)

```ts
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';
import { VitePWA } from 'vite-plugin-pwa';
import path from 'node:path';

export default defineConfig({
  plugins: [
    react(),
    VitePWA({ /* ver sección PWA */ }),
  ],
  resolve: { alias: { '@': path.resolve(__dirname, './src') } },
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' },
  },
  build: { outDir: '../backend/src/main/resources/static' },
});
```

- **Alias:** `@/` → `./src` (absoluto).
- **Dev proxy:** `/api` → backend Spring Boot en `localhost:8080` (para Fase 5+).
- **Build:** `outDir` apunta a `../backend/src/main/resources/static` para el build integrado del JAR (Fase 11). En dev esto no se usa.

---

## 5. TypeScript (`tsconfig.json`)

- `"strict": true`.
- `"baseUrl": "."` y `paths: { "@/*": ["src/*"] }`.
- `"moduleResolution": "bundler"`, `"target": "ES2020"`, `"jsx": "react-jsx"`.
- `"types"` incluye `vite/client`.
- No `any`. Si se necesita, justificar con comentario.

---

## 6. Tailwind CSS y Tema

### Paleta neón (definida en AGENTS.md)

| Variable | Hex | Uso |
|---|---|---|
| `--neon-cyan` | `#00f3ff` | Acentos, bordes, glow |
| `--neon-pink` | `#ff00ff` | Highlights, botones |
| `--neon-purple` | `#bc13fe` | Gradientes, fondos |
| `--neon-yellow` | `#faff00` | Destacados puntuales |

### Estrategia dark/light

- Tailwind en `class` strategy (Dark Mode `'class'`).
- Clase `dark` se aplica al `<html>` desde `themeStore` (persistida).
- **Default: dark** (estética neón).
- Colores de superficie, texto y acento definidos como CSS variables en `:root` (light) y `.dark`. Tailwind los consume vía tokens.

### Diseño

- Mobile-first: breakpoints `sm` (640), `md` (768), `lg` (1024), `xl` (1280).
- Fuente: Inter (via CDN o system-ui fallback) configurada en Tailwind.
- Transiciones suaves (300ms) al alternar tema.
- Scrollbar personalizada (delgada, color neón) vía CSS.
- Glow suave (`box-shadow` con color neón) en elementos interactivos.

---

## 7. Estructura React

### Principios

- **Funcional components + hooks** únicamente. Sin class components.
- TypeScript strict. Props tipadas.
- Componente que acepta `className` para composición.

### Routing (React Router v6)

| Ruta | Página |
|---|---|
| `/` | `HomePage` |
| `/library` | `LibraryPage` |
| `/search` | `SearchPage` |
| `/settings` | `SettingsPage` |
| `*` | `NotFoundPage` |

(La ruta `/player` se añadirá en Fase 7; se deja preparada la estructura.)

### Layout

- `Layout.tsx`: contenedor `flex` column; `TopHeader` arriba, contenido scrolleable en medio, `BottomNav` abajo (fija).
- `TopHeader.tsx`: logo/título NeonVibe, botón toggle tema, avatar placeholder.
- `BottomNav.tsx`: barra fija inferior (min 64px) con iconos Home / Library / Search / Settings. Destaca la ruta activa.

---

## 8. Estado Global (Zustand)

Stores en `src/stores/`:

### `themeStore.ts`
- `theme: 'dark' | 'light'`
- `setTheme(theme)`, `toggleTheme()`
- Persistencia en `localStorage` (clave `neonvibe-theme`).
- Aplica/elimina la clase `dark` en `<html>`.

### `authStore.ts` (placeholder)
- `isAuthenticated: boolean`
- `user: { id, email, name, avatarUrl } | null`
- `token: string | null`
- `login()`, `logout()` (stubs para Fase 1 backend).

### `playerStore.ts` (placeholder)
- `isPlaying: boolean`
- `currentTrack: { id, title, artist, album, durationSeconds } | null`
- `progress: number`
- `play()`, `pause()`, `setTrack()`, `seek()` (stubs para Fase 7).

---

## 9. PWA

### `vite-plugin-pwa` (GenerateSW)

```ts
VitePWA({
  registerType: 'autoUpdate',
  includeAssets: ['favicon.svg', 'icon-192.svg', 'icon-512.svg'],
  manifest: {
    name: 'NeonVibe',
    short_name: 'NeonVibe',
    description: 'Servidor de música personal self-hosted',
    theme_color: '#0a0a0f',
    background_color: '#0a0a0f',
    display: 'standalone',
    start_url: '/',
    icons: [
      { src: '/icon-192.svg', sizes: '192x192', type: 'image/svg+xml', purpose: 'any maskable' },
      { src: '/icon-512.svg', sizes: '512x512', type: 'image/svg+xml', purpose: 'any maskable' },
    ],
  },
  workbox: { globPatterns: ['**/*.{js,css,html,svg,png,ico}'] },
});
```

- Como los iconos son SVG placeholder (squares neón), se documenta que para producción real se necesitan PNG de 192/512 y máscaras adecuadas (Fase 9).
- Estrategia: **precache de assets estáticos** (CacheFirst via Workbox). Runtime cache de API se deja para Fase 9.
- Si `vite-plugin-pwa` falla en esta fase, atajo documentado: `public/manifest.json` + service worker manual mínimo que hace `cache.addAll` del shell.

### `public/manifest.json`
- Definido igual que arriba (se genera también el `index.html` enlaza `manifest.webmanifest` que inyecta el plugin; se mantiene `manifest.json` en `public/` para entornos sin plugin).

---

## 10. Mobile-first / Touch

- Bottom nav: altura mínima `64px` + `padding-bottom: env(safe-area-inset-bottom)`.
- Touch targets: mínimo `48px` de alto/ancho en controles.
- Header: sticky, con `padding-top: env(safe-area-inset-top)`.
- Viewport objetivo 375px → se escala a 1440px.
- Content usa `overflow-y-auto` con padding inferior suficiente para no quedar tapado por la nav.

---

## 11. Estilos globales (`src/styles/globals.css`)

- Directivas `@import "tailwindcss"` (v4) o `@tailwind base/components/utilities` (v3).
- CSS variables de tema en `:root` y `.dark`.
- Transición global de colores.
- Scrollbar personalizada.
- Fuente Inter.

---

## 12. Estructura de Archivos a Crear

```
neonvibe/frontend/
├── .gitignore
├── .npmrc
├── package.json
├── pnpm-lock.yaml          (generado por pnpm install)
├── tsconfig.json
├── tsconfig.node.json      (para vite.config.ts)
├── vite.config.ts
├── index.html
├── README.md
├── public/
│   ├── manifest.json
│   ├── icon-192.svg
│   ├── icon-512.svg
│   └── favicon.svg
└── src/
    ├── main.tsx
    ├── vite-env.d.ts
    ├── App.tsx
    ├── index.css            (o styles/globals.css)
    ├── components/
    │   ├── Layout.tsx
    │   ├── TopHeader.tsx
    │   ├── BottomNav.tsx
    │   ├── Button.tsx
    │   ├── Card.tsx
    │   ├── IconButton.tsx
    │   └── Skeleton.tsx
    ├── pages/
    │   ├── HomePage.tsx
    │   ├── LibraryPage.tsx
    │   ├── SearchPage.tsx
    │   ├── SettingsPage.tsx
    │   └── NotFoundPage.tsx
    ├── stores/
    │   ├── themeStore.ts
    │   ├── authStore.ts
    │   └── playerStore.ts
    ├── hooks/               (vacío preparado para Fase 5)
    ├── api/                 (vacío preparado para Fase 5)
    ├── types/
    │   └── index.ts         (Tipos de Track/Album/Artist/User compartidos)
    └── utils/
        └── cn.ts            (helper className merge)
```

---

## 13. Dependencias

### Dependencies
`react`, `react-dom`, `react-router-dom`, `zustand`, `@tanstack/react-query`, `axios`, `howler`, `lucide-react`

### DevDependencies
`typescript`, `vite`, `@vitejs/plugin-react`, `tailwindcss`, `@tailwindcss/vite` (o `postcss`+`autoprefixer`), `@types/react`, `@types/react-dom`, `@types/howler`, `vite-plugin-pwa`

---

## 14. Criterios de Aceptación (resumen de PHASES.md)

1. `pnpm dev` levanta en `localhost:5173` sin errores de consola.
2. Se ve bien en 375px (mobile) y 1440px (desktop).
3. Toggle dark/light funciona y persiste en `localStorage`.
4. Navegación entre rutas sin recarga.
5. PWA instalable (manifest válido en Chrome DevTools).
6. `pnpm build` termina correctamente.

---

## 15. Riesgos y Mitigaciones

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Tailwind v4 + dark class-mode con CSS vars | Medio | Documentar fallback a Tailwind v3.4 |
| `vite-plugin-pwa` incompatibilidad de versión | Medio | Atajo: `public/manifest.json` + SW manual |
| Safe areas en navegadores iOS | Bajo | Usar `env(safe-area-inset-*)` |
| Node 22 vs 20 (dev vs objetivo) | Bajo | Compatible; fijar `engines` en package.json |
| Icons SVG no soportados en PWA Android | Bajo | Documentar necesidad de PNG (Fase 9) |

---

*Documento de especificación — Fase 4. Fecha: 2026-08-06.*
