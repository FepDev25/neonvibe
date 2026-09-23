# Fase 9 — PWA, Offline y Polish: Especificación

> **Fase:** 9 de 11
> **Tipo:** Frontend (PWA/offline) + polish responsive.
> **Dependencias:** Fase 4-8 (shell, player, library, playlists, covers).
> **Objetivo:** La app se siente nativa: instalable con iconos reales, funciona parcialmente sin conexión (tracks descargados reproducibles) y tiene polish desktop/gestos.

---

## 1. Alcance

**PWA / Service Worker**
- Iconos PNG reales (192/512, maskable, apple-touch) + favicon.
- SW con precache de assets (CacheFirst implícito por GenerateSW).
- **Runtime caching**: API GET (playlists, favoritos, library) con **StaleWhileRevalidate**; covers con **CacheFirst** (ignoreSearch por `?token=`); stream **no** se cachea en SW (se usa IndexedDB para offline).

**Offline**
- Descarga de tracks a **IndexedDB** con progreso (botón "Descargar" en álbum y playlist).
- Reproducción offline: el player usa un **blob URL** desde IndexedDB cuando está sin conexión (o ante error de red) si el track está descargado.
- Indicador de estado: badge "Sin conexión" en el header.
- Lista de descargas y borrado.

**Polish**
- Layout **desktop con sidebar** (nav lateral en `lg+`), mobile mantiene bottom nav.
- **Pull-to-refresh** en Library/Favorites.
- **Swipe entre tabs** en Library.
- Splash screen + theme-color coherentes con el tema neón.

**Fuera de alcance:** background playback nativo avanzado (MediaSession ya cubre), sincronización de descargas multi-dispositivo, streaming sin red (solo descargado), radio/scrobbling (Fase 10).

---

## 2. Arquitectura Offline

```
src/offline/
  offlineDb.ts       # wrapper IndexedDB (tracks: id, blob, metadatos, size, downloadedAt)
  offlineStore.ts    # Zustand: estado de descargas + progreso + acciones (ensureLoaded, download*, remove)
  useOnline.ts       # hook: navigator.onLine + listeners
src/player/
  playable.ts        # getPlayableSrc(trackId): URL red (online) o blob URL (offline+descargado)
```

### 2.1 `offlineDb` (IndexedDB)
- DB `neonvibe-offline`, store `tracks` (keyPath `id`), índice `downloadedAt`.
- API promisificada: `put(record)`, `get(id)`, `delete(id)`, `keys()`, `getAll()`.
- No depende de React; singleton.

### 2.2 `offlineStore` (Zustand)
- Estado: `downloads: Record<number, {status: 'idle'|'downloading'|'done'|'error', progress: number}>`.
- `ensureLoaded()` — carga los ids descargados desde DB (promise única, StrictMode-safe).
- `isDownloaded(id)`, `getStatus(id)`.
- `downloadTrack(track)` — `fetch(streamUrl)` con header JWT, lee el body como `ReadableStream` contando bytes (progreso real), `createObjectURL`+`blob` → guarda en DB.
- `downloadBatch(tracks)` — secuencial (una por una), actualiza progreso por track y un contador global.
- `removeTrack(id)`, `downloadedTracks()` (metadatos para listar).

### 2.3 Player offline (`playable.ts` + playerStore)
- `getPlayableSrc(trackId)`: si `navigator.onLine` → URL de stream normal; si offline y descargado → blob URL (cacheado en memoria).
- `playerStore.loadAndPlay` usa `getPlayableSrc` (await).
- Ante evento `error` del audio: si el track está descargado y NO se estaba usando el blob → reintentar con blob URL (una sola vez); si ya se usaba o no hay descarga → skip a siguiente (comportamiento actual).

### 2.4 SW runtime caching
```js
runtimeCaching: [
  { urlPattern: ({url, request}) => request.method === 'GET' &&
      url.pathname.startsWith('/api/v1/') &&
      !/\/stream$|\/cover$/.test(url.pathname),
    handler: 'StaleWhileRevalidate',
    options: { cacheName: 'neonvibe-api', expiration: { maxEntries: 100 } } },
  { urlPattern: ({url}) => /\/cover$/.test(url.pathname),
    handler: 'CacheFirst',
    options: { cacheName: 'neonvibe-covers', cacheableResponse: { statuses: [0, 200] },
               expiration: { maxEntries: 500, maxAgeSeconds: 2592000 },
               matchOptions: { ignoreSearch: true } } },
]
```

---

## 3. UI

### 3.1 Descargas
- `DownloadButton` (componente): estados `Descargar` / `Descargando X%` (barra) / `Descargado ✓` (con acción de borrar).
- En `AlbumDetailPage` y `PlaylistDetailPage` (header, junto a Favoritos/Editar).

### 3.2 Badge offline
- `TopHeader`: si offline → píldora "Sin conexión" (icono WifiOff).

### 3.3 Layout desktop (sidebar)
```
<aside class="fixed inset-y-0 left-0 hidden w-60 lg:flex ...">  // Sidebar
  logo, nav (Inicio, Biblioteca, Playlists, Favoritos, Buscar, Ajustes)
</aside>
<main class="flex-1 lg:pl-60">
  <TopHeader/>
  <Outlet/>
</main>
<BottomNav class="lg:hidden"/>
<PlayerBar (bottom: móvil sobre nav; desktop bottom-0, left-60) />
```
- Desktop: 2-3 columnas (grids ya responsivos) + sidebar; mobile single column + bottom nav.

### 3.4 Gestos
- `PullToRefresh`: wrapper touch (solo cuando `scrollTop===0` y tirar hacia abajo > ~60px) → dispara `onRefresh` (invalida queries de la página). Spinner neón mientras.
- Swipe en `LibraryPage`: `touchstart/touchend` horizontal → cambia tab (`|dx| > 50`).

### 3.5 Splash / index.html
- `index.html`: splash neón inline dentro de `#root` (reemplazado al montar React), `theme-color`, `<link rel="manifest">`, `apple-touch-icon`.

---

## 4. Iconos

- Generar PNG desde SVG (rsvg-convert / ImageMagick):
  - `icon-192.png`, `icon-512.png` (purpose `any`).
  - `maskable-192.png`, `maskable-512.png` (fondo completo, contenido en zona segura ~80%).
  - `apple-touch-icon.png` (180×180).
- Actualizar `vite.config.ts` (manifest con PNG + `includeAssets`) y `public/manifest.json`.
- favicon: se mantiene SVG (compatible moderno).

---

## 5. Criterios de Aceptación

1. `pnpm build` (tsc strict) + SW generado con runtime caching y precache de PNG.
2. Manifest con iconos PNG válidos (192/512/maskable) → instalable en Android (Chrome DevTools → Application).
3. Descargar álbum/playlist guarda los tracks en IndexedDB con progreso visible.
4. En modo avión (tras descargar y visitar las páginas), el reproductor reproduce un track descargado desde el blob URL.
5. Offline, la app shell carga (SW precache) y las queries cacheadas (SWR) responden stale.
6. Desktop muestra sidebar (nav lateral); mobile bottom nav. Grids 2-3 columnas en desktop.
7. Pull-to-refresh y swipe de tabs funcionan.
8. Badge "Sin conexión" visible offline.

---

## 6. Riesgos y Mitigaciones

| Riesgo | Impacto | Mitigación |
|---|---|---|
| IndexedDB con archivos grandes (50MB MP3) | Cuota/rendimiento | Descarga secuencial + progreso; almacenamiento persistente (`navigator.storage.persist`) |
| Blob URL gigante en memoria | Memoria | `URL.revokeObjectURL` al cambiar de track si ya no se usa |
| SWR cache desactualizada | Datos stale en UI | `networkTimeoutSeconds` + refetch al volver online |
| Offline sin auth (dev bootstrap) | App sin sesión offline | El token persiste en localStorage (`persist`); SWR sirve stale sin re-login |
| Verificación offline headless difícil | Validación parcial | Build + lógica + checklist manual en pendientes |

---

*Documento de especificación — Fase 9. Fecha: 2026-08-06.*
