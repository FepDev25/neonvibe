# Fase 9 — PWA, Offline y Polish: Review

> **Fase:** 9 de 11
> **Base:** `docs/specs/fase-9-pwa-offline-spec.md`, `docs/plans/fase-9-pwa-offline-plan.md`
> **Estado:** ✅ APROBADO (con fixes aplicados tras revisión)

---

## 1. Verificación (esta PC)

| Checkpoint | Resultado |
|---|---|
| `pnpm build` (tsc strict + SW) | ✅ Exit 0; SW con runtimeCaching |
| Manifest PNG (192/512/maskable) | ✅ `manifest.webmanifest` generado por el plugin, sin duplicados |
| SW runtime rules | ✅ `neonvibe-api` (SWR) + `neonvibe-covers` (SWR, ignoreSearch) |
| Precache | ✅ 41 entradas (incluye PNGs) |
| Rutas SPA | ✅ 200 |

*(Offline real requiere validación manual en navegador — ver pendientes.)*

## 2. Cambios clave

**PWA / SW**
- Iconos PNG reales (192/512, maskable con zona segura, apple-touch) generados desde SVG neón.
- Runtime caching: API GET con **StaleWhileRevalidate** (offline sirve último dato bueno), covers con SWR + `ignoreSearch` (URLs con `?token=`; revalida tras upload).
- Manifest único (plugin); favicon PNG + apple-touch.

**Offline**
- `offlineDb` (IndexedDB) con `tx` que espera `oncomplete`/`onabort` (commit real) y `onversionchange`.
- `offlineStore` (Zustand): descarga con progreso real (ReadableStream), **AbortController** (cancelable), secuencial, no revoca blobs en reproducción.
- `playable.ts`: `getPlayableSrc` (blob si offline) + `getOfflineBlobUrl`.
- `playerStore`: `loadAndPlay` async con **generation guard** (no race audio/UI), fallback a blob en `_onError`, cola offline desde cache local en `restoreFromServer`.
- `DownloadButton` en álbum/playlist (Descargar → % → Descargado, con borrado).
- Badge "Sin conexión" en `TopHeader`.

**Polish**
- Sidebar desktop (`lg+`), bottom nav solo mobile, player bar responsive.
- `PullToRefresh` (touch-action + spinner durante refresh) en Library/Favorites.
- Swipe entre tabs en Library.
- Splash neón en `index.html`.

## 3. Hallazgos del revisor y correcciones aplicadas

Revisor: **APROBAR CON CAMBIOS**. Corregidos antes del commit:
1. **Race en `loadAndPlay`** (blob tardío de A pisaba a B) → generation guard (`loadGen`).
2. **`offlineDb.tx` resolvía antes del commit real** → `oncomplete`/`onerror`/`onabort` + error no-null; `onversionchange`.
3. **Descarga no cancelable** → `AbortController` por track, abort en `removeTrack`.
4. **`removeTrack` revocaba un blob en reproducción** → guard contra `currentTrack`.
5. **Progreso 0% sin Content-Length** → spinner indeterminado en `DownloadButton`.
6. **Covers stale tras upload (CacheFirst 30d)** → cubiertas a StaleWhileRevalidate.
7. **`audio.src !== src` (absoluto vs relativo)** → comparación por `currentLoadId` + flag offline.
8. **Cola vacía offline** → `restoreFromServer` hidrata desde `loadQueueCache()` si falla el backend.
9. **Manifest duplicado** → solo el del plugin; borrado `public/manifest.json`.
10. **PullToRefresh competía con el scroll nativo** → `touch-action` + estado `refreshing`.

## 4. Pendientes documentados

- **Validación manual en navegador**: instalar PWA (Android standalone), modo avión con tracks descargados, recarga offline con datos SWR, pull-to-refresh y swipe, sidebar desktop.
- El cache API SWR es por-dispositivo (single-user); anotado para multi-user.
- `devBootstrap` offline en dev falla silenciosamente (esperado; PROD usa OAuth).

---

*Review — Fase 9. Fecha: 2026-08-06.*
