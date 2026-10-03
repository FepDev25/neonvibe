# Fase 3 — Streaming y WebSocket: Revisión

> Fecha: 2026-08-06
> Revisado por: Subagente Fase 3 (streaming)
> Base: spec (`docs/specs/fase-3-streaming-spec.md`) y plan (`docs/plans/fase-3-streaming-plan.md`)

---

## 1. Resumen

Se implementó el streaming de audio con **HTTP Range Requests** (`GET /api/v1/tracks/{id}/stream`)
y la capa **WebSocket STOMP** para sync del reproductor. Se añadió lógica de dominio a
`PlayQueueService` (advance/retreat/shuffle/repeat/sync), registro de historial con
umbrales (`PlayHistoryService.recordIfSignificant`), DTOs de mensajes WS (sin entidades
JPA), autenticación JWT en WebSocket (interceptor de CONNECT + handshake handler) y un
puente de eventos del scanner (`ScannerWsBridge` → `SCANNER_PROGRESS` / `NEW_TRACKS`).

## 2. Resultados de compilación y tests

| Prueba | Resultado |
|--------|-----------|
| `./mvnw clean compile` | **PASS** (0 errores) |
| `./mvnw clean test` | **PASS** (92/92, 0 failures, 0 errors) |

### Detalle de tests (92 total, 10 clases de test)

| Clase de test | Tests | Resultado |
|---------------|-------|-----------|
| `service/StreamServiceTest` (nuevo) | 12 | PASS |
| `controller/StreamControllerTest` (nuevo) | 4 | PASS |
| `config/WebSocketConfigTest` (nuevo) | 2 | PASS |
| `websocket/PlayerWebSocketControllerTest` (nuevo) | 6 | PASS |
| `service/PlayQueueServiceTest` (nuevo) | 10 | PASS |
| `service/PlayHistoryServiceTest` (nuevo) | 6 | PASS |
| Fase 0-2 (SecurityConfig, Auth, Track, GlobalException, scanner, repos, etc.) | 52 | PASS |

Se añadieron **6 clases de test nuevas** (40 tests nuevos), cumpliendo el mínimo de 5.

## 3. Checklist de criterios de aceptación

| Ítem | Estado |
|------|--------|
| `GET /tracks/{id}/stream` con HTTP Range (`206 Partial Content`) y seek HTML5 | ✅ PASS — StreamServiceTest cubre start=0, start=mid, end>EOF (clamp), open-ended (`start-`), suffix (`-N`), 416, MIME, 404; StreamControllerTest verifica 206 + `Content-Range`/`Accept-Ranges`/`Content-Length` |
| Determinación de MIME por `format`/`mimeType` | ✅ PASS — `StreamService.resolveContentType` (mimeType → mapa por formato → octet-stream), `StreamServiceTest.missingMime_fallsBackToFormatMap` |
| WebSocket STOMP: endpoint `/ws`, broker `/topic`+`/queue`, prefijo `/app` | ✅ PASS — `WebSocketConfig` + `WebSocketConfigTest` (addEndpoint `/ws`, con SockJS, broker, prefijos) |
| Auth JWT en WebSocket | ✅ PASS (parcial) — `WebSocketAuthInterceptor` (CONNECT, header `Authorization` o query `token`), `UserPrincipalHandshakeHandler` (query `token` → `Principal`). Ver §6 nota |
| `PLAYER_SYNC`, `QUEUE_UPDATED`, `SCANNER_PROGRESS`, `NEW_TRACKS` clientes | ✅ PASS — DTOs + `PlayerWebSocketController` + `ScannerWsBridge` (+ hook en `MusicScannerService`) |
| `PLAY`/`PAUSE`/`SEEK`/`NEXT`/`PREV`/`UPDATE_QUEUE` | ✅ PASS — `@MessageMapping` en `PlayerWebSocketController` |
| `PlayQueue` API advance/retreat/shuffle/repeat/sync | ✅ PASS — `PlayQueueServiceTest` (advance NONE/ALL/ONE, retreat, sync, updateQueue) |
| `PlayHistory` recording por umbrales | ✅ PASS — `PlayHistoryServiceTest` (completed, >50%, >30s, <umbral no registra) |
| Scanner → `SCANNER_PROGRESS`/`NEW_TRACKS` broadcast | ✅ PASS (básico) — `ScannerWsBridge` + `MusicScannerService.publishScanEvents` + `LibrarySyncService.drainNewTrackIds` |
| No exponer entidades en WS; solo DTOs | ✅ PASS — payloads WS son records `*Message`/`*Request`; services devuelven la entidad internamente solo para broadcasts |
| `.docs` actualizado + commit por etapa | ✅ PASS |

## 4. Decisiones y deviaciones respecto al spec/plan

1. **Streaming con `FileChannel` manual en lugar de `ResourceRegion`** (deviación de la
   sugerencia del spec §3.3). Motivo: en `@WebMvcTest`/MockMvc, el
   `ResourceRegionHttpMessageConverter` lanza `UnsupportedOperationException` al intentar
   mutar las cabeceras de respuesta (colección read-only de MockMvc), produciendo un 500
   en tests aunque en un contenedor real funcionaría. Se implementó un
   `InputStreamResource` sobre un `FileChannel` posicionado en `start` con un
   `FilterInputStream` acotado a `length` bytes: equivalente en comportamiento y 100%
   testeable. Esto mantiene el streaming fragmentado (no carga el archivo completo en
   memoria) y soporta seek.
2. **`UserPrincipal` ahora implementa `java.security.Principal`** (deviation de Fase 1):
   necesario para que el handshake handler devuelva un `Principal` y `@MessageMapping`
   reciba una identidad autenticada. Añade `getName()`; sin cambios de comportamiento en
   REST (id/email/name intactos).
3. **`WebSocketAuthInterceptor` y `UserPrincipalHandshakeHandler` son `@Component`** para
   poder inyectarse en `WebSocketConfig` (evita `NoSuchBeanDefinitionException` al arrancar).
4. **`MusicScannerService` constructor** ahora acepta `ScannerWsBridge @Autowired(required=false)`
   para no romper contextos sin STOMP; se actualizó `MusicScannerServiceTest` en
   consecuencia.
5. **`LibrarySyncService.upsert`** recoge ids de tracks creados en un queue thread-safe y
   expone `drainNewTrackIds()` para `NEW_TRACKS`.
6. **`SecurityConfig`** añade `/ws/**` como público (el handshake lleva su propio JWT por
   query/header STOMP); el resto de `/api/**` sigue autenticado.

## 5. Verificación manual del Range logic (edge cases)

Verificado por `StreamServiceTest`:

| Caso | Cabecera | Resultado esperado | PASS |
|------|----------|--------------------|------|
| Sin Range | — | `200`, `Content-Length: 1024` | ✅ |
| start=0 | `bytes=0-99` | `206`, rango 0-99, len 100 | ✅ |
| start=mid | `bytes=500-799` | `206`, len 300 | ✅ |
| end>EOF | `bytes=900-5000` | `206`, clamp a 900-1023, len 124 | ✅ |
| open-ended | `bytes=512-` | `206`, 512-1023, len 512 | ✅ |
| start≥EOF | `bytes=4096-` | `416` | ✅ |
| malformed | `items=0-100` | `416` | ✅ |
| suffix | `bytes=-200` | `206`, 824-1023, len 200 | ✅ |

## 6. Notas / pendientes para fases siguientes

- **WebSocket auth (parcial):** el interceptor valida JWT en CONNECT y el handshake handler
  expone el `Principal`, pero el pipeline completo STOMP→`@MessageMapping` con
  `Principal` solo se puede verificar con un cliente real (wscat / @stomp/stompjs) en un
  entorno con servidor levantado, que no está disponible en el sandbox de tests. El flujo
  está cableado y compilado; **endurecer/verificar end-to-end en Fase 7/9** (reconnection
  backoff, token rotation por WS).
- **Integración real con PostgreSQL / servidor:** la migración y entidades ya se validaron
  en Fase 2; el streaming usa filesystem, no DB, por lo que un `docker compose up` +
  `GET /tracks/{id}/stream` contra un MP3 real de `/srv/Music` queda para verificación en
  el servidor (permisos `felipep:felipep`), como en fases anteriores.
- **Crossfade / `PLAYER_SYNC` y re-emisión idempotente:** el reproductor (Fase 7) debe
  resincronizar leyendo `PLAYER_SYNC` al reconectar; el backend ya re-emite estado sin
  estado opaco.
- **Progreso incremental del scanner:** esta fase publica `SCANNER_PROGRESS` al completar
  scans y `NEW_TRACKS` al detectar archivos nuevos; un progreso por-archivo más fino se
  deja para Fase 8/10 si se desea.

## 7. Bloqueantes

Ninguno. Todos los tests compilan y pasan (92/92). Los únicos ítems no verificables en el
sandbox son la conexión WS con un cliente real y el streaming sobre un MP3 real en el
servidor (requieren entorno con dependencias/librería montada).

---

*Última actualización: 2026-08-06*
