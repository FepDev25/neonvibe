# Fase 7 — Reproductor Frontend y Sync: Especificación

> **Fase:** 7 de 11
> **Tipo:** Frontend (reproductor real) + fix backend puntual (auth por query param).
> **Dependencias:** Fase 3 (streaming Range, queue/history, WebSocket STOMP), Fase 5 (api layer, componentes).
> **Objetivo:** El reproductor funciona: stream con seek, barra fija, cola, shuffle/repeat, sync WebSocket multi-tab, MediaSession e historial.

---

## 1. Alcance

- Reproductor con `HTMLAudioElement` apuntando a `/api/v1/tracks/{id}/stream` (Range requests → seek).
- Barra fija inferior (sobre la bottom nav): track actual, play/pause, next/prev, seek bar, shuffle, repeat, botón de cola.
- Cola visual (bottom sheet) con salto a track.
- Shuffle y repeat (NONE/ALL/ONE) persistidos en backend (`PUT /queue`).
- Cliente WebSocket STOMP (SockJS) en `/ws`: envía `PLAY/PAUSE/SEEK/NEXT/PREV`, recibe `PLAYER_SYNC` y `QUEUE_UPDATED`. Sync entre pestañas del mismo usuario.
- `MediaSession API`: metadatos + controles en lockscreen/notificación.
- Registro de historial: `POST /history` al completar un track y al cambiar de track (significativo).

**Fuera de alcance:** visualizador de audio (Fase 8), crossfade (post-MVP), página `/player` a pantalla completa (se añade si sobra; el bar cubre el requisito), descarga offline (Fase 9).

---

## 2. Contratos Backend

### 2.1 Streaming con auth para `<audio>`

El endpoint `/api/v1/tracks/{id}/stream` está protegido y un `HTMLAudioElement` **no puede** mandar el header `Authorization`. Solución: el `JwtAuthenticationFilter` acepta también el token en query param (`?token=<JWT>`), con precedencia del header cuando exista.

```
GET /api/v1/tracks/{id}/stream?token=<access_token>
→ 200 / 206 Partial Content (Range) / 416
```

### 2.2 Queue (existente)

- `GET /api/v1/queue` → `PlayQueueResponse`
- `PUT /api/v1/queue` con `{current_track_id?, position_seconds, shuffle_enabled, repeat_mode: NONE|ALL|ONE, tracks_order: Long[]}`

`PlayQueueResponse`: `id, current_track_id, position_seconds, shuffle_enabled, repeat_mode, tracks_order, updated_at`.

### 2.3 History (existente)

- `POST /api/v1/history` con `{track_id, completed?, duration_listened_seconds?}` → `PlayHistoryResponse`.
- `GET /api/v1/history` → `Page<PlayHistoryResponse>`.

### 2.4 WebSocket (existente, Fase 3)

- Endpoint `/ws` (SockJS). Auth: `?token=` en el handshake (ya soportado por `UserPrincipalHandshakeHandler`) o header STOMP `Authorization`.
- Enviar a `/app/player/{play,pause,seek,next,prev}` con `{action, position_seconds}`; y `/app/queue/update` con `{tracks_order, current_track_id}`.
- Suscribir a `/topic/sync/{userId}`: `PLAYER_SYNC` = `{track_id, position_seconds, is_playing, timestamp, queue_id}` y `QUEUE_UPDATED` = `{user_id, tracks_order, current_track_id}`.

---

## 3. Arquitectura Frontend

```
src/
├── api/
│   └── queue.ts             # getQueue/updateQueue/recordHistory (+ types)
├── player/
│   ├── playerStore.ts       # store Zustand real (audio singleton + cola + controles)
│   ├── sync.ts              # cliente STOMP/SockJS (conectar, enviar, recibir, reconectar)
│   └── mediaSession.ts      # actualización de MediaSession (metadatos + handlers)
├── components/
│   ├── PlayerBar.tsx        # barra fija superior a la bottom nav
│   ├── SeekBar.tsx          # input range estilizado (seek)
│   └── QueueSheet.tsx       # bottom sheet con la cola
└── pages/                   # integración playTrack(queue)
```

### 3.1 `playerStore` (reproductor real)

Estado: `queue: PlayerTrack[]`, `currentIndex`, `currentTrack`, `isPlaying`, `progress`, `duration`, `volume`, `shuffle`, `repeat: 'NONE'|'ALL'|'ONE'`, `hasNext`, `hasPrev`.

- **Audio singleton** a nivel de módulo (`const audio = new Audio()`), `src = /api/v1/tracks/{id}/stream?token=<jwt>` (token leído de `authStore.getState().token`).
- Eventos: `timeupdate`→progress, `loadedmetadata`→duration, `ended`→history(completed)+next/reseek según repeat, `error`→skip a siguiente.
- Acciones:
  - `playTrack(track, queue?)` — si `queue`, la usa y ubica el índice; si no, `queue=[track]`.
  - `toggle()`, `next()`, `prev()` — avanzan/retroceden en la cola respetando shuffle y repeat.
  - `seek(seconds)`, `setVolume(v)`.
  - `toggleShuffle()`, `cycleRepeat()`.
  - `restoreQueue(queueState)` — al arrancar, desde `GET /queue`.
- **Persistencia:** cada cambio relevante (cola, current, posición al cambiar track, shuffle, repeat) → `PUT /queue` (debounce) + envía acción WS. Así el estado sobrevive recargas y se sincroniza.
- **Shuffle:** `next` con shuffle elige índice aleatorio ≠ actual; se mantiene `queue` ordenada (se aleatoriza el índice, no el array). Repeat ALL envuelve, ONE repite.

### 3.2 Sync WS (`sync.ts`)

- Conexión única (singleton): `Stomp.Client` sobre SockJS `new SockJS('/ws?token=...')` (SockJS usa la URL del endpoint; el token va como query param de la URL base de SockJS).
  - Nota: con SockJS el token debe ir en la **URL** del endpoint SockJS (`/ws?token=`) para que `UserPrincipalHandshakeHandler` lo lea.
- `subscribe('/topic/sync/' + userId)` → aplica `PLAYER_SYNC` y `QUEUE_UPDATED`.
- Guard anti-eco: al enviar una acción local se marca `suppressUntil = now + 1500ms`; los broadcasts recibidos durante esa ventana se ignoran (la pestaña emisora ya aplicó localmente; evita reinicios de audio entre pestañas).
- Reconexión: si se desconecta, reconectar con backoff (1s, 2s, 4s… máx 30s); al reconectar, re-suscribir y pedir estado (`GET /queue` + store local).
- **Aplicación del sync entrante:** si `track_id !== current` → `playTrack`/cambiar pista (con `is_playing` para play/pause); si es la misma pista → solo ajusta `progress`/`isPlaying` cuando el desfase es significativo (>2s).

### 3.3 MediaSession (`mediaSession.ts`)

- En cada cambio de track: `navigator.mediaSession.metadata = new MediaMetadata({title, artist, album, artwork: [cover placeholder]})`.
- Handlers: `play/pause/previoustrack/nexttrack/seekto` → acciones del store.

---

## 4. Componentes

### `SeekBar`
- `input type=range` con `min=0 max=duration`, valor `progress`. Estilizado neón.
- Al soltar (onPointerUp/onChange commit) → `seek(seconds)` + acción WS.
- Muestra tiempo actual / duración (formato `formatDuration`).

### `PlayerBar`
- Fija: `fixed inset-x-0 bottom-[calc(64px+env(safe-area-inset-bottom))]`, `bg-surface`, borde superior.
- Contenido (compacto): mini cover (`AlbumCover` pequeña), título + artista (1 línea ellipsis), botones prev / play-pause / next, shuffle, repeat, y botón de cola (icono lista).
- `SeekBar` en la parte superior de la barra (borde de 3px) para que el seek sea accesible en móvil.
- Oculto si no hay `currentTrack`.
- Expandir al tocar el título/cover → `QueueSheet` (o info). Por MVP: tocar cover/título abre la cola.

### `QueueSheet`
- Bottom sheet: lista de `queue` con el actual resaltado (icono playing), tap → `playTrack(track, queue)`.
- Cierre con X / overlay.

---

## 5. Integración en páginas

- `TrackRow` y `ReorderTrackRow`: el botón play ahora llama `playTrack(track, queue)` con la lista del contexto.
- Páginas que pasan su lista: `LibraryPage` (tracks), `AlbumDetailPage`, `ArtistDetailPage`, `SearchPage`, `FavoritesPage`, `PlaylistDetailPage`.
- `Layout`: renderiza `PlayerBar` sobre `BottomNav`; padding inferior del main pasa de `pb-24` a `pb-40` cuando hay reproductor.
- `App`: boot → `restoreQueue` (GET /queue) + conectar WS (`sync.connect()`).

---

## 6. Historial (POST /history)

- En `ended` → `recordHistory(track, completed=true)`.
- En cambio de track manual (next/prev/selección de otra pista) → `recordIfSignificant`-equivalente **cliente**: registrar con `completed=false` y `duration_listened_seconds = progress` si el progreso supera 30s o la mitad de la duración (misma lógica que el backend). Para MVP se registra siempre en `ended` y en cambios con ≥30s escuchados.
- El backend también registra en `/player/next` (recordIfUseful); ambos son idempotentes en contenido (se toleran duplicados en MVP, anotado).

---

## 7. Criterios de Aceptación

1. Play/pause/seek funciona (mobile + desktop) contra el stream real (206/seek).
2. next/prev y clic en otra pista actualizan el stream.
3. La cola se sincroniza entre pestañas del mismo usuario vía WS (PLAYER_SYNC/QUEUE_UPDATED).
4. MediaSession muestra título/artista y controles nativos.
5. `GET /history` refleja reproducciones (completadas y parciales ≥30s).
6. La cola persiste al recargar (`GET /queue`).
7. `pnpm build` (tsc strict) y `./mvnw test` verdes.

---

## 8. Riesgos y Mitigaciones

| Riesgo | Impacto | Mitigación |
|---|---|---|
| `<audio>` no autentica stream | Alto | `?token=` en query param (JwtAuthenticationFilter) |
| Eco de broadcasts WS (reinicio de audio) | Alto | Guard `suppressUntil` al enviar acciones |
| Reconexión WS rota la suscripción | Medio | Re-suscribir + resync de cola al reconectar |
| Progress/seek desincronizados entre pestañas | Bajo | Solo aplicar sync si desfase >2s o cambio de track |
| Autoplay bloqueado por navegador | Medio | El play siempre inicia por gesto de usuario (click) |
| SockJS + token en URL | Medio | Token vía URL del endpoint SockJS (HandshakeHandler lo lee) |

---

*Documento de especificación — Fase 7. Fecha: 2026-08-06.*
