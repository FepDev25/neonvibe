# Fase 7 — Reproductor Frontend y Sync: Plan de Implementación

> **Fase:** 7 de 11
> **Base:** `docs/specs/fase-7-player-spec.md`
> **Estado:** Frontend (reproductor) + fix backend puntual.

---

## 1. Orden de Pasos

1. **Backend — auth por query param:** `JwtAuthenticationFilter` acepta `?token=`. Test en `StreamControllerTest` (stream con token query param → 206/200).
2. **Deps frontend:** `@stomp/stompjs`, `sockjs-client`, `@types/sockjs-client`.
3. **Tipos + API:** `PlayQueue`, `PlayHistory`, `RepeatMode`; `api/queue.ts`.
4. **`playerStore`:** reescribir con audio singleton, cola, shuffle/repeat, seek, volumen, eventos, persistencia (PUT /queue debounced).
5. **`sync.ts`:** cliente STOMP/SockJS con reconexión y guard anti-eco.
6. **`mediaSession.ts`:** metadatos + handlers.
7. **Componentes:** `SeekBar`, `PlayerBar`, `QueueSheet`.
8. **`Layout`:** PlayerBar + padding ajustado.
9. **Integración:** `playTrack` en TrackRow/ReorderTrackRow y páginas; boot en `App`.
10. **Checkpoints de prueba.**

---

## 2. Secuencia de Archivos

### Backend
```
security/JwtAuthenticationFilter.java    (query param fallback)
test/controller/StreamControllerTest.java (+test query token)
```

### Frontend
```
package.json / pnpm-lock.yaml   (@stomp/stompjs, sockjs-client, @types/sockjs-client)
types/index.ts                  (+PlayQueue, PlayHistory, RepeatMode)
api/queue.ts                    (nuevo)
player/playerStore.ts           (reescritura completa)
player/sync.ts                  (nuevo)
player/mediaSession.ts          (nuevo)
components/SeekBar.tsx          (nuevo)
components/PlayerBar.tsx        (nuevo)
components/QueueSheet.tsx       (nuevo)
components/Layout.tsx           (PlayerBar + padding)
components/TrackRow.tsx         (playTrack + prop queue)
components/ReorderTrackRow.tsx  (playTrack + prop queue)
pages/LibraryPage.tsx, AlbumDetailPage.tsx, ArtistDetailPage.tsx,
pages/SearchPage.tsx, FavoritesPage.tsx, PlaylistDetailPage.tsx  (pasar queue)
App.tsx                         (boot: restoreQueue + sync.connect)
```

---

## 3. Checkpoints de Prueba

| # | Checkpoint | Cómo verificar |
|---|---|---|
| C1 | `./mvnw test` (incluye test query token) | Exit 0 |
| C2 | Stream con `?token=` devuelve 206 con Range | curl |
| C3 | `pnpm build` (tsc strict) | Exit 0 |
| C4 | `PUT /queue` persiste (shuffle/repeat/tracks_order) | curl + GET |
| C5 | `POST /history` aparece en `GET /history` | curl |
| C6 | WS STOMP conecta y recibe/envía (test browser) | manual |
| C7 | Play/seek cambia el audio en el navegador | manual |
| C8 | Cola sobrevive reload | manual |
| C9 | MediaSession muestra controles nativos | manual (Android/desktop) |

---

## 4. Riesgos y Contingencias

| Riesgo | Señal | Plan B |
|---|---|---|
| `@stomp/stompjs` no compatible con versión | import falla | Fallback a `@stomp/stompjs` v7 stable; o manual WebSocket + SockJS |
| SockJS token no llega al handshake | 403/401 en WS | Verificar URL `/ws?token=`; fallback header STOMP Authorization |
| Autoplay policy | play no arranca | Todo play parte de gesto de usuario |
| Echo loop | audio se reinicia | Guard `suppressUntil` (1500ms) |

---

## 5. Reglas de Código

- Audio: un único `HTMLAudioElement` (singleton) — compatible con MediaSession y `createMediaElementSource` (Fase 8).
- Store Zustand no guarda el elemento de audio; solo estado. El elemento vive en el módulo.
- Persistencia de cola con debounce (~500ms) para no spamear `PUT /queue`.
- Sync WS: local-first, broadcasts son best-effort con guard anti-eco.
- Commits convencionales en inglés.

---

*Plan de implementación — Fase 7. Fecha: 2026-08-06.*
