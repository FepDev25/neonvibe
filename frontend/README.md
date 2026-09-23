# NeonVibe — Frontend

Shell de UI del servidor de música **NeonVibe**. Vite + React 18 + TypeScript
(strict) + Tailwind CSS + Zustand + React Router v6 + PWA.

> **Estado:** Fase 4 (shell y tema). La integración con el backend llega en
> Fase 5 (librería) y Fase 7 (reproductor).

## Requisitos

- Node 20+ (validado con Node 22)
- pnpm (`npm i -g pnpm` o via corepack)

## Instalación

```bash
cd frontend
pnpm install
```

## Desarrollo

```bash
pnpm dev
```

Levanta Vite en `http://localhost:5173`. Las llamadas a `/api` se proxían a
`http://localhost:8080` (backend Spring Boot), listo para Fase 5.

## Build

```bash
pnpm build
```

Compila TypeScript y genera el bundle de producción en
`../backend/src/main/resources/static` (para el build integrado del JAR en
Fase 11).

## Previsualizar el build

```bash
pnpm preview
```

## Tema

- Oscuro (neón) por defecto, claro opcional.
- Toggle en el header y en Ajustes. Persiste en `localStorage` (`neonvibe-theme`).
- Paleta neón definida en `src/index.css`:

| Variable | Hex |
|---|---|
| `--color-neon-cyan` | `#00f3ff` |
| `--color-neon-pink` | `#ff00ff` |
| `--color-neon-purple` | `#bc13fe` |
| `--color-neon-yellow` | `#faff00` |

## PWA

- `vite-plugin-pwa` con estrategia GenerateSW (precache de assets estáticos).
- Manifest: nombre NeonVibe, `standalone`, iconos SVG placeholder.
- **Pendiente (Fase 9):** iconos PNG reales de 192/512 con máscara, splash
  screen y runtime cache de API/offline.

## Rutas

| Ruta | Página |
|---|---|
| `/` | Inicio |
| `/library` | Biblioteca |
| `/search` | Buscar |
| `/settings` | Ajustes |
| `*` | 404 |

## Estructura

```
src/
├── components/   # Layout, TopHeader, BottomNav, Button, Card, IconButton, Skeleton
├── pages/        # Home, Library, Search, Settings, NotFound
├── stores/       # themeStore, authStore, playerStore (Zustand)
├── types/        # Tipos compartidos (Track, Album, Artist, User)
├── utils/        # cn() — merge de clases
├── api/          # (preparado para Fase 5)
├── hooks/        # (preparado para Fase 5)
├── App.tsx
└── index.css     # Tailwind v4 + paleta neón + dark/light
```
