# Fase 9 — PWA, Offline y Polish: Plan de Implementación

> **Fase:** 9 de 11
> **Base:** `docs/specs/fase-9-pwa-offline-spec.md`
> **Estado:** Frontend (PWA/offline/polish). Sin cambios de backend.

---

## 1. Orden de Pasos

1. **Iconos:** SVG mejorado → PNG (192/512, maskable, apple-touch) con rsvg-convert.
2. **vite.config.ts:** manifest con PNG + runtimeCaching (API SWR, covers CacheFirst).
3. **public/manifest.json:** actualizar a PNG.
4. **Offline infra:** `offlineDb.ts`, `offlineStore.ts` (Zustand), `useOnline.ts`.
5. **Player offline:** `playable.ts` (getPlayableSrc) + playerStore (loadAndPlay async + fallback blob en error).
6. **UI descargas:** `DownloadButton` en AlbumDetailPage y PlaylistDetailPage; badge offline en TopHeader.
7. **Layout desktop:** `Sidebar.tsx` + ajustes Layout/PlayerBar/BottomNav.
8. **Gestos:** `PullToRefresh` + swipe de tabs en LibraryPage.
9. **Splash + index.html.**
10. **Checkpoints.**

---

## 2. Secuencia de Archivos

### Iconos / PWA
```
public/icon-192.png, icon-512.png, maskable-192.png, maskable-512.png, apple-touch-icon.png
public/icon-source.svg (rediseño)
vite.config.ts   (manifest PNG + runtimeCaching)
public/manifest.json
index.html       (splash, theme-color, manifest, apple-touch)
```

### Offline
```
src/offline/offlineDb.ts
src/offline/offlineStore.ts
src/offline/useOnline.ts
src/player/playable.ts
src/stores/playerStore.ts   (getPlayableSrc + fallback blob)
```

### UI / Polish
```
src/components/Sidebar.tsx
src/components/DownloadButton.tsx
src/components/PullToRefresh.tsx
src/components/TopHeader.tsx      (badge offline)
src/components/Layout.tsx         (sidebar, paddings)
src/components/BottomNav.tsx      (lg:hidden)
src/components/PlayerBar.tsx      (bottom responsive)
src/pages/AlbumDetailPage.tsx     (DownloadButton)
src/pages/PlaylistDetailPage.tsx  (DownloadButton)
src/pages/LibraryPage.tsx         (swipe tabs + pull-to-refresh)
src/pages/FavoritesPage.tsx       (pull-to-refresh)
```

---

## 3. Checkpoints de Prueba

| # | Checkpoint | Cómo verificar |
|---|---|---|
| C1 | `pnpm build` (tsc strict) + SW | Exit 0; `sw.js` con runtimeCaching |
| C2 | Manifest PNG válido | build output + DevTools Application |
| C3 | Descarga guarda en IndexedDB | navegador (Application → IndexedDB) |
| C4 | Reproducción offline (modo avión) | navegador |
| C5 | SWR: recargar offline muestra datos previos | navegador |
| C6 | Sidebar desktop + bottom nav mobile | DevTools responsive 375/1440 |
| C7 | Pull-to-refresh + swipe tabs | navegador |
| C8 | Badge offline | navegador (offline) |

---

## 4. Riesgos y Contingencias

| Riesgo | Señal | Plan B |
|---|---|---|
| IndexedDB no disponible | download falla | Guard `'indexedDB' in window`; deshabilitar botones |
| Blob URL memory leak | — | Revocar al cambiar de track |
| SW no actualiza en dev | cambios viejos | `registerType: 'autoUpdate'` + skipWaiting (build) |
| Verificación offline | — | Checklist manual en pendientes |

---

## 5. Reglas de Código

- Offline: una sola conexión IndexedDB; `ensureLoaded` idempotente.
- Descargas secuenciales (evita saturar red/cuota).
- El player intenta red primero (online) y solo usa blob offline/error.
- Desktop sidebar sólo `lg+`; mobile mantiene bottom nav.
- Commits convencionales en inglés.

---

*Plan de implementación — Fase 9. Fecha: 2026-08-06.*
