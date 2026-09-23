# Fase 6 — Playlists y Favoritos: Especificación

> **Fase:** 6 de 11
> **Tipo:** Frontend + extensiones backend menores (endpoints faltantes)
> **Dependencias:** Fase 2 (entidades/API), Fase 5 (api layer, hooks, componentes, dev auth).
> **Objetivo:** CRUD de playlists (crear, editar, borrar, añadir/quitar tracks, reordenar) y sistema de favoritos (heart en tracks/álbumes/artistas con vista `/favorites` por tabs).

---

## 1. Alcance

- **Playlists:** listado `/playlists`, detalle `/playlist/:id` (read-only si es pública de otro usuario), crear/editar/borrar (solo owner), añadir/quitar tracks, reordenar con botones ↑/↓.
- **Add-to-playlist:** modal desde cualquier track row con la lista de playlists propias.
- **Favoritos:** heart toggle en `TrackRow`, `AlbumCard`, `ArtistCard` y cabeceras de detalle; vista `/favorites` con tabs (Canciones/Álbumes/Artistas).
- **Backend:** endpoints que faltan para soportar lo anterior (detalle de playlist con tracks completos, favoritos enriquecidos con entidades), eliminación del índice único de `position` (documentado en pendientes).

**Fuera de alcance:** drag-and-drop nativo (se usan botones ↑/↓), carátulas de playlist reales (Fase 8), share público vía URL sin login (Fase 10).

---

## 2. Contratos Backend

Base `/api/v1`, snake_case.

### 2.1 Playlists (existentes + añadidos)

| Método + Ruta | Body | Respuesta |
|---|---|---|
| `GET /playlists` | — | `Playlist[]` (propias + públicas) |
| `GET /playlists/{id}` | — | `PlaylistDetail` (añadido) |
| `POST /playlists` | `{name, description?, is_public?}` | `Playlist` (201) |
| `PUT /playlists/{id}` | `{name, description?, is_public?}` | `Playlist` |
| `DELETE /playlists/{id}` | — | 204 |
| `POST /playlists/{id}/tracks` | `{track_id}` | `Playlist` (201) |
| `DELETE /playlists/{id}/tracks/{trackId}` | — | 204 |
| `POST /playlists/{id}/reorder` | `{track_ids: Long[]}` | `Playlist` |

### 2.2 DTOs

- **`Playlist`:** `id, name, description, is_public, cover_art_path, owner_id, created_at, updated_at, tracks: [{id, track_id, position}]`
  - **Cambio:** se añade `owner_id` para que el frontend distinga propias vs públicas de otros.
- **`PlaylistDetail`** (nuevo): `id, name, description, is_public, cover_art_path, owner_id, created_at, updated_at, tracks: Track[]` (tracks completos, ordenados por `position`).
- **`ReorderRequest`:** `{track_ids: Long[]}` (lista completa ordenada).

### 2.3 Favoritos (existentes + añadidos)

| Método + Ruta | Query | Respuesta |
|---|---|---|
| `GET /favorites` | `entityType?=TRACK\|ALBUM\|ARTIST` | `Favorite[]` |
| `POST /favorites` | `{entity_type, entity_id}` | `Favorite` (201) |
| `DELETE /favorites/{id}` | — | 204 |
| `GET /favorites/tracks` | — | `Track[]` (añadido, enriquecido) |
| `GET /favorites/albums` | — | `Album[]` (añadido, enriquecido) |
| `GET /favorites/artists` | — | `Artist[]` (añadido, enriquecido) |

- **`Favorite`:** `id, entity_type, entity_id, created_at`.
- Entidades enriquecidas: mismos DTOs que Fase 5 (`TrackResponse`, `AlbumResponse` con `track_count`, `ArtistResponse`).

### 2.4 Migración Flyway

- `V4__drop_playlist_tracks_position_unique.sql`: `DROP INDEX IF EXISTS uq_playlist_tracks_position;`
  - Elimina el riesgo de violación transitoria del unique `(playlist_id, position)` durante reorder (decisión documentada en `docs/pendientes.md`). El servicio ya reindexa y ordena por posición en app.

---

## 3. Reglas de Negocio

- **Ownership:** solo el owner puede `PUT/DELETE` playlist, añadir/quitar/reordenar tracks. El backend ya lo valida (`requireOwned`). El detalle `GET /playlists/{id}` es accesible para el owner o si `is_public=true`; si es privada de otro usuario → 404 (no filtrar existencia).
- **Add track:** no duplica (`existsByPlaylistIdAndTrackId`). Se añade al final (`position = count`).
- **Reorder:** el frontend envía la lista completa de `track_ids` en el nuevo orden; el backend reindexa 0..n-1.
- **Favorito duplicado:** el backend lanza 409/IllegalState si ya existe → el frontend evita el toggle doble con estado optimista.

---

## 4. Arquitectura Frontend

```
src/
├── api/
│   ├── playlists.ts        # CRUD playlists + tracks + reorder
│   └── favorites.ts        # list, create, delete + listas enriquecidas
├── hooks/
│   ├── usePlaylists.ts     # useQuery + useMutation (invalida ['playlists', id])
│   └── useFavorites.ts     # store Zustand + toggle optimista
├── stores/
│   └── favoritesStore.ts   # set de claves `type:id` + toggle con API
├── components/
│   ├── PlaylistCard.tsx
│   ├── FavoriteButton.tsx
│   ├── PlaylistForm.tsx    # modal crear/editar (nombre, desc, público)
│   ├── AddToPlaylistSheet.tsx
│   └── ReorderTrackRow.tsx # track row con ↑/↓ y quitar
└── pages/
    ├── PlaylistsPage.tsx
    ├── PlaylistDetailPage.tsx
    └── FavoritesPage.tsx
```

### 4.1 `favoritesStore` (Zustand)

- Estado: `keys: Set<string>` (claves `"TRACK:12"`, `"ALBUM:3"`, `"ARTIST:7"`), `loaded: boolean`.
- Acciones:
  - `ensureLoaded()` — primera vez llama `GET /favorites` y puebla el set (una sola vez; promise a nivel módulo para StrictMode).
  - `isFavorite(type, id)` — membresía.
  - `toggle(type, id)` — optimista: añade/elimina del set, llama `POST`/`DELETE`, revierte en error; invalida las queries de `/favorites/*` del TanStack cache.
- Los componentes leen `isFavorite` directamente del store → el corazón se refleja en toda la app al instante (criterio de aceptación).

### 4.2 TanStack Query

- `usePlaylists()` → `useQuery(['playlists'])`.
- `usePlaylist(id)` → `useQuery(['playlist', id])` (usa `GET /playlists/{id}`).
- Mutations con `invalidateQueries`:
  - `useCreatePlaylist`, `useUpdatePlaylist`, `useDeletePlaylist`
  - `useAddTrackToPlaylist`, `useRemoveTrackFromPlaylist`, `useReorderPlaylist`
- `useFavoriteTracks()`, `useFavoriteAlbums()`, `useFavoriteArtists()` → queries de las listas enriquecidas (para `/favorites`).

---

## 5. Componentes

### `FavoriteButton`
- Botón corazón: `Heart` (vacío) vs `Heart` con `fill` (favorito). Color `neon-pink` cuando activo.
- `entityType` + `entityId` props; lee/usa `favoritesStore`.
- Touch target ≥ 44px (48px por defecto); `aria-pressed` + `aria-label` "Marcar como favorito"/"Quitar de favoritos".

### `PlaylistCard`
- `Link` a `/playlist/:id`. Cover placeholder (gradient determinístico del nombre), nombre, `N canciones`, badge "Pública" si `is_public`. Si `owner_id !== currentUser.id` → badge "De {ownerName}"? No tenemos owner name; mostrar solo "Pública" y ocultar acciones.

### `PlaylistForm`
- Modal centrado (overlay + panel): campos nombre (obligatorio), descripción (textarea), toggle público/privado. Botones Cancelar / Guardar.
- Modos: crear (`POST /playlists`) y editar (`PUT /playlists/:id`).

### `AddToPlaylistSheet`
- Bottom sheet con la lista de **playlists propias** (`GET /playlists` filtrado por `owner_id`).
- Cada fila: nombre + check si el track ya está. Tap → `POST /playlists/{id}/tracks` (o no-op si ya está), feedback visual, cierre.
- Se abre desde el botón `+` del `TrackRow`.

### `ReorderTrackRow`
- `TrackRow` + columna de acciones: botones ↑ / ↓ (deshabilitados en extremos) y quitar (trash).
- `onMove(direction)` → el padre calcula el nuevo orden y llama `useReorderPlaylist`.

---

## 6. Páginas

### `/playlists`
- Header "Playlists" + botón "Nueva playlist" → `PlaylistForm` (crear).
- Grid de `PlaylistCard` (propias y públicas). Solo las propias tienen acciones (la card es link; las acciones viven en el detalle).

### `/playlist/:id`
- Header: cover, nombre, descripción, badge público/privado, nº tracks, duración total.
- Acciones (solo owner): Editar (form), Eliminar (con confirm), y por track: reordenar ↑/↓ y quitar.
- Tracklist de `Track` completos (del detalle enriquecido) con `ReorderTrackRow`.
- Si no es owner: vista read-only (`ReorderTrackRow` sin acciones, `FavoriteButton` sí).
- Si 404 → estado "playlist no encontrada".

### `/favorites`
- Tabs Canciones / Álbumes / Artistas.
- Cada tab usa la query enriquecida correspondiente. Canciones → `TrackRow`, Álbumes → `AlbumCard`, Artistas → `ArtistCard`.
- Estado vacío: "Aún no has marcado favoritos".

---

## 7. Integración en Componentes Existentes

- `TrackRow`: añadir `FavoriteButton` y botón `+` (AddToPlaylistSheet) a la derecha.
- `AlbumCard`: overlay `FavoriteButton` (top-right sobre el cover).
- `ArtistCard`: overlay `FavoriteButton`.
- `AlbumDetailPage`: botón "Añadir a playlist" en el header (añade todos los tracks) — opcional; se añade por-track vía `TrackRow`+.
- `HomePage` y `LibraryPage`: enlaces rápidos a `/playlists` y `/favorites`.
- `App.tsx`: rutas `/playlists`, `/playlist/:id`, `/favorites`.

---

## 8. Criterios de Aceptación

1. Crear playlist, añadir tracks, reordenar (↑/↓), quitar y eliminar funciona end-to-end (owner).
2. Marcar favorito persiste en DB y se refleja en la UI inmediatamente (todas las vistas).
3. `/favorites` muestra tracks/álbumes/artistas favoritos con tabs.
4. Playlist pública de otro usuario: se ve read-only; el owner puede editar.
5. El detalle de playlist muestra tracks completos (no solo ids).
6. `pnpm build` (tsc strict) y `./mvnw test` sin errores.
7. Flyway V4 aplica sin problemas (índice único de position eliminado) y reorder no lanza violación de constraint.

---

## 9. Riesgos y Mitigaciones

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Reorder viola unique `(playlist_id, position)` | Alto | V4 elimina el índice; el servicio reindexa en app |
| Favorito toggle doble por race | Medio | Estado optimista + dedupe en `favoritesStore` |
| `GET /playlists/{id}` de privada ajena filtra existencia | Medio | Devolver 404 genérico (mismo que "no existe") |
| Playlists propias vs públicas indistinguibles | Alto | Se añade `owner_id` al DTO |
| Modal/sheet en móvil | Bajo | Bottom sheet con safe-area; overlay scroll |

---

*Documento de especificación — Fase 6. Fecha: 2026-08-06.*
