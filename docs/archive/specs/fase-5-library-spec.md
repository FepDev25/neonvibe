# Fase 5 — Librería y Navegación: Especificación

> **Fase:** 5 de 11
> **Tipo:** Frontend (integración con APIs backend reales)
> **Dependencias:** Fase 2 (endpoints tracks/albums/artists), Fase 4 (shell, tema, routing, stores).
> **Objetivo:** Ver la música. Grids de álbumes, listas de tracks y artistas conectados a datos reales, con navegación a detalle de álbum/artista y búsqueda server-side.

---

## 1. Alcance

- Cliente HTTP (axios) con inyección de JWT y manejo de 401.
- Tipos TypeScript exactos de los DTOs backend (verificado contra respuesta real).
- Hooks TanStack Query para tracks, álbumes, artistas, detalle de álbum/artista.
- Vista `/library` con tabs **Canciones / Álbumes / Artistas**.
- Rutas de detalle: `/album/:id`, `/artist/:id`.
- Rutas dedicadas de navegación: `/albums`, `/artists` (según rutas de AGENTS.md).
- Componentes: `AlbumCard`, `ArtistCard`, `TrackRow`, cover placeholder (Fase 8 traerá carátulas reales).
- Búsqueda server-side con debounce en `/search`.
- Scroll infinito / "cargar más" con paginación `page`/`size`.
- **Dev auth bootstrap:** en entorno `DEV`, login automático con token mock (Google auth desactivado en dev). En prod queda pendiente el flujo OAuth real (ver sección 8).

**Fuera de alcance:** reproductor funcional/streaming (Fase 7), carátulas online (Fase 8), playlists/favoritos (Fase 6), PWA offline (Fase 9).

---

## 2. Contratos Backend (verificados contra backend real en esta PC)

Base URL: `/api/v1`. Todos los campos en `snake_case` (Jackson `SNAKE_CASE`).

### 2.1 Paginación (Spring `Page<T>` serializada)

```json
{
  "content": [...],
  "pageable": { "page_number": 0, "page_size": 20, "offset": 0, "paged": true, "unpaged": false },
  "last": true,
  "total_pages": 1,
  "total_elements": 9,
  "size": 20,
  "number": 0,
  "sort": { "empty": true, "sorted": false, "unsorted": true },
  "first": true,
  "number_of_elements": 9,
  "empty": false
}
```

### 2.2 Endpoints usados

| Método + Ruta | Query params | Respuesta |
|---|---|---|
| `GET /tracks` | `q, artist, album, genre, year, page, size` | `Page<Track>` |
| `GET /tracks/{id}` | — | `Track` |
| `GET /albums` | `q, artist, page, size` | `Page<Album>` |
| `GET /albums/{id}` | — | `Album` |
| `GET /albums/{id}/tracks` | `page, size` | `Track[]` (List) |
| `GET /artists` | `q, page, size` | `Page<Artist>` |
| `GET /artists/{id}` | — | `Artist` |
| `GET /artists/{id}/albums` | — | `Album[]` (List) |
| `GET /artists/{id}/tracks` | — | `Track[]` (List) |
| `POST /auth/google` | body `{"id_token": string}` | `AuthResponse` |

### 2.3 DTOs (snake_case)

- **Track:** `id, file_path, title, artist, album, album_artist, year, genre, track_number, disc_number, duration_seconds, bitrate, format, mime_type, has_lyrics, cover_art_path, is_available, created_at, updated_at`
- **Album:** `id, name, artist, year, genre, cover_art_path, created_at, track_count`
- **Artist:** `id, name, created_at`
- **AuthResponse:** `access_token, refresh_token, token_type, expires_in`

### 2.4 Errores

Estructura `{error, message, timestamp}` con `@ControllerAdvice`. Códigos: `401` (auth), `404` (not found), `400` (validación).

---

## 3. Arquitectura Frontend

```
src/
├── api/
│   ├── client.ts          # axios instance + interceptor JWT + manejo 401
│   ├── library.ts         # funciones tipadas tracks/albums/artists
│   ├── auth.ts            # googleLogin + fetchMe
│   └── devBootstrap.ts    # login automático mock en DEV (solo dev)
├── hooks/
│   ├── useLibrary.ts      # hooks TanStack Query
│   ├── useInfinitePage.ts # paginación + IntersectionObserver
│   └── useDebounce.ts
├── components/
│   ├── AlbumCover.tsx     # placeholder gradient determinístico
│   ├── AlbumCard.tsx
│   ├── ArtistCard.tsx
│   └── TrackRow.tsx
├── pages/
│   ├── LibraryPage.tsx    # tabs Canciones/Álbumes/Artistas
│   ├── AlbumDetailPage.tsx
│   ├── ArtistDetailPage.tsx
│   ├── AlbumBrowserPage.tsx  # ruta /albums
│   ├── ArtistBrowserPage.tsx # ruta /artists
│   └── SearchPage.tsx     # reescrita con búsqueda real
└── types/index.ts         # tipos snake_case + Page<T>
```

---

## 4. Cliente HTTP (`client.ts`)

- `axios.create({ baseURL: '/api/v1', timeout: 15000 })`.
- Request interceptor: `Authorization: Bearer <token>` desde `useAuthStore.getState().token`.
- Response interceptor: en `401` → `useAuthStore.getState().logout()` y rechaza (el dev bootstrap reloguea en siguiente montaje).
- Respuesta vacía (204/head) → `null`.

---

## 5. TanStack Query

- Query keys canónicas: `['tracks', filters]`, `['albums', filters]`, `['artists', filters]`, `['album', id]`, `['album', id, 'tracks']`, `['artist', id]`, `['artist', id, 'albums']`, `['artist', id, 'tracks']`.
- Paginación: `page` en los filters; `placeholderData: keepPreviousData` para evitar flicker.
- Scroll infinito: hook `useInfinitePage` mantiene `page` y un sentinel `<div>` con `IntersectionObserver` que dispara `loadMore` cuando `!last`.
- `staleTime: 60s` (ya configurado global), `refetchOnWindowFocus: false`.

---

## 6. Componentes

### `AlbumCover`
- Placeholder hasta Fase 8: gradient lineal de 2 colores de una paleta neón, elegidos **determinísticamente** por hash del nombre (`name` + `artist`).
- `aspect-square`, `rounded-2xl`. Si en el futuro hay `cover_art_path`, se renderiza `<img>` (hook deja el campo listo; Fase 8 lo activa).

### `AlbumCard`
- `Link` a `/album/:id`. Contenido: `AlbumCover`, nombre (1 línea ellipsis), artista, `track_count` canciones.

### `ArtistCard`
- `Link` a `/artist/:id`. Avatar circular gradient con inicial del nombre + nombre.

### `TrackRow`
- `number` (o índice), título + artista, duración formateada, botón play que llama `playerStore.setTrack({...})` (reproductor real en Fase 7; aquí solo setea estado).

---

## 7. Páginas

### `/library` (tabs)
- Tabs: Canciones | Álbumes | Artistas (estado local, `sm` centrado).
- Cada tab renderiza su grid/lista con hooks y skeletons (`Skeleton`).
- "Cargar más" vía `useInfinitePage` (scroll infinito con sentinel).
- Canciones: lista de `TrackRow`. Álbumes: grid `AlbumCard` (2 col mobile → 4+ desktop). Artistas: grid `ArtistCard`.

### `/album/:id`
- Header: `AlbumCover` grande, nombre, artista, año, `track_count`, duración total.
- Tracklist ordenada por `track_number` con `TrackRow`.

### `/artist/:id`
- Header: avatar + nombre.
- Sección "Álbumes" (grid de `AlbumCard` vía `getArtistAlbums`).
- Sección "Canciones principales" (lista de `TrackRow` vía `getArtistTracks`).

### `/albums` y `/artists`
- Reutilizan los mismos grids (misma lógica que los tabs de library, full-width). Sirven para las rutas listadas en AGENTS.md.

### `/search`
- Input con debounce (300ms). Con `q` no vacío: 3 secciones server-side — Canciones (`/tracks?q=`), Álbumes (`/albums?q=`), Artistas (`/artists?q=`), cada una con sus props limitadas (primera página).
- Estado vacío ("sin resultados") y skeletons mientras carga.

---

## 8. Auth en Dev (bootstrap)

- El backend en profile `dev` desactiva la validación Google (`GOOGLE_AUTH_ENABLED=false`) y acepta cualquier `id_token` como mock.
- `devBootstrap.ts`: si `import.meta.env.DEV` y no hay sesión → `POST /auth/google {"id_token":"dev-bootstrap"}` → `GET /auth/me` → `authStore.setAuth`.
- Guard para StrictMode (promise a nivel de módulo, ejecuta una sola vez).
- **En producción** (`import.meta.env.PROD`) NO se autologuza: queda el flujo OAuth real como pendiente documentado (login UI + redirect Google). Settings muestra la tarjeta de sesión con nombre/email y botón "Cerrar".

---

## 9. Formato

- `utils/format.ts`: `formatDuration(seconds)` → `"M:SS"` (o `"H:MM:SS"` si ≥ 1h), sin decimales. `formatCount(n)` → n + " canciones"/"canción".

---

## 10. Criterios de Aceptación (resumen de PHASES.md)

1. `pnpm build` (tsc strict) sin errores.
2. `/library` muestra álbumes/canciones/artistas reales desde el backend dev (data seed `/tmp/test-music`).
3. Click en álbum → `/album/:id` con tracklist; click en artista → `/artist/:id` con álbumes y canciones.
4. Búsqueda filtra resultados server-side sin recargar.
5. Scroll infinito carga la página 2+ cuando hay más datos.
6. Skeletons de carga presentes.
7. `GET /artists/{id}/albums` devuelve álbumes del artista (fix backend incluido).

---

## 11. Riesgos y Mitigaciones

| Riesgo | Impacto | Mitigación |
|---|---|---|
| JSON de `Page` cambia por naming strategy | Alto | Verificado contra respuesta real; tipo `Page<T>` fiel |
| Auth requerido en `/api/**` sin UI de login | Alto | Dev bootstrap con mock (backend dev lo acepta) |
| Scroll infinito duplica items | Medio | Query key incluye filters; sentinel con guard de `last` |
| `/artists/{id}/albums` vacío (bug) | Medio | Fix backend: buscar por columna `artist`, no `name` |
| Cover placeholders feos | Bajo | Gradient determinístico con paleta neón |

---

*Documento de especificación — Fase 5. Fecha: 2026-08-06.*
