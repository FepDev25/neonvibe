# Fase 7 — Reproductor Frontend y Sync: Review

> **Fase:** 7 de 11
> **Base:** `docs/specs/fase-7-player-spec.md`, `docs/plans/fase-7-player-plan.md`
> **Estado:** ✅ APROBADO (con fixes aplicados tras revisión)

---

## 1. Verificación End-to-End (esta PC)

| Checkpoint | Resultado |
|---|---|
| `./mvnw test` | ✅ 97 tests, 0 fallos (+1 stream con `?token=`) |
| `pnpm build` (tsc strict) | ✅ Exit 0 |
| Stream `?token=` + Range | ✅ 206 con `Content-Range`/`Accept-Ranges`/`Content-Length` |
| Stream `?token=` sin Range / inválido | ✅ 200 / 401 |
| `PUT/GET /queue` (shuffle, repeat, orden) | ✅ persiste |
| `POST/GET /history` | ✅ registra y lista |
| **WebSocket STOMP end-to-end (Node)** | ✅ handshake `?token=` → CONNECT → SUBSCRIBE → PLAY/SEEK/NEXT → broadcasts `PLAYER_SYNC` + `QUEUE_UPDATED` |
| **Echo originator** | ✅ `originator` viaja de vuelta en la broadcast (ej. `"client-XYZ"`) |
| Rutas SPA | ✅ 200 |

## 2. Cambios clave de la fase

**Backend**
- `JwtAuthenticationFilter` acepta `?token=` solo para `/api/v1/tracks/` y `/api/v1/albums/` (el `<audio>` no envía headers; superficie mínima para no filtrar JWT en URLs/logs).
- DTOs WS con `originator`: `PlayerActionMessage`, `PlayerSyncMessage`, `QueueUpdateMessage`, `QueueUpdateRequest`; el controller lo refleja en cada broadcast. Permite a cada cliente ignorar su propio eco.

**Frontend**
- `playerStore` reescrito: `HTMLAudioElement` singleton, cola + `currentIndex`, shuffle/repeat, seek, volumen, persistencia `PUT /queue` debounced (500ms), historial `POST /history` con dedupe, `restoreFromServer`, `_applyPlayerSync`/`_applyQueueUpdate`.
- `sync.ts`: STOMP/SockJS singleton, token fresco en cada reconexión, guard anti-eco por `originator` (sin ventanas temporales), guards de tipo, `disconnectSync`.
- `mediaSession.ts`: metadatos + handlers `play/pause/next/prev/seekto/seekbackward/seekforward`.
- `PlayerBar` (fixed sobre bottom nav, responsive: shuffle/repeat/volumen `hidden sm:flex`), `SeekBar` (commit en pointerup + keyboard), `QueueSheet`.
- Integración: `TrackRow`/`ReorderTrackRow` usan `playTrack(track, queue)`; todas las páginas pasan su lista como cola; `App` hace boot auth → WS → restore.

## 3. Hallazgos del revisor y correcciones aplicadas

Revisor: **APROBAR CON CAMBIOS**. Corregidos antes del commit:
1. **Reconexión con token caducado** — la factory SockJS ahora re-lee el token en cada (re)conexión; si no hay token, se desconecta. Refresh-token rotación completa queda pendiente (prod OAuth).
2. **`suppressUntil` tragaba broadcasts de otras pestañas** — reemplazado por echo de `originator` (cada cliente ignora solo su propio eco). Verificado end-to-end.
3. **Seek del sync descartado con `duration===0`** — el seek remoto se aplica siempre que difiera >2s; `audio.currentTime` es no-op hasta `loadedmetadata` (state queda sincronizado igualmente).
4. **`broadcastEnabled` a través de `await`** — eliminado (las acciones internas de sync no emiten broadcasts; las acciones de usuario siempre lo hacen).
5. **PlayerBar overflow a 375px** — shuffle/repeat/volumen ocultos en `<sm`.
6. **`next()` con repeat ONE forzaba play** — ahora respeta `isPlaying` (si está pausado, solo busca 0).
7. **`_onError` bucle infinito** — tope de 3 errores consecutivos (reset en `canplay`).

Mejoras menores aplicadas: `seekbackward`/`seekforward` en MediaSession, `onPointerCancel` y Enter en SeekBar, guards de tipo en sync.ts, comentario de autoplay en restore.

## 4. Riesgos documentados (v0.2 / prod)

- `Cache-Control: private, no-transform` no evita cachear la URL con token en el media cache; para prod considerar `no-store` o token de streaming de corta duración.
- Refresh token del WS cuando el JWT expira (rotación completa) — con prod OAuth.
- `disconnectSync` exportado pero sin wiring en logout (el dev user no cierra sesión).
- Historial: `POST /history` (cliente) + `recordIfUseful` (servidor en NEXT) pueden duplicar entradas en MVP.

## 5. Pendientes documentados

- Verificación **visual** en navegador: play/pause/seek, multi-tab sync, MediaSession en Android, cola tras reload.
- Visualizador de audio (Fase 8) puede usar `audio` vía `createMediaElementSource`.
- Arte de MediaSession (carátulas reales en Fase 8).

---

*Review — Fase 7. Fecha: 2026-08-06.*
