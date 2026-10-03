# Fase 3 — Streaming y WebSocket: Plan de Implementación

> Versión: 1.0 — 2026-08-06
> Base: `docs/specs/fase-3-streaming-spec.md`
> Repo: `neonvibe` (backend + docs únicamente)

---

## 1. Orden de implementación

| Paso | Tarea | Archivos |
|------|-------|----------|
| 1 | Servicio de streaming con Range (no depende de WS) | `service/StreamService.java`, `infra/FileStorageResolver.java` (si aplica) |
| 2 | Controller REST de stream | `controller/StreamController.java` |
| 3 | Config STOMP base | `config/WebSocketConfig.java` |
| 4 | Auth JWT WS (interceptor + handshake handler) | `websocket/WebSocketAuthInterceptor.java`, `websocket/UserPrincipalHandshakeHandler.java` |
| 5 | DTOs WS | `websocket/dto/*.java` (5 DTOs) |
| 6 | Controller de mensajes WS | `websocket/PlayerWebSocketController.java` + `service/PlayQueueService` enriquecido |
| 7 | History + Scanner bridge | `service/PlayHistoryService.java`, `scanner/*` bridge, `LibrarySyncService` reporte |
| 8 | Tests | 6 clases nuevas + ajustes |
| 9 | Revisión | `docs/reviews/fase-3-streaming-review.md` |

---

## 2. Secuencia de archivos (dependencias)

```
1.  StreamService            (puro IO, sin dto)
2.  StreamController         (usa StreamService + Track entity lookup)
3.  WebSocketConfig          (broker + endpoint, depende de interceptor)
4.  WebSocketAuthInterceptor (depende de JwtTokenProvider)
5.  UserPrincipalHandshakeHandler
6.  DTOs WS (5 records)
7.  PlayQueueService          (ampliar con advance/retreat/shuffle/repeat/sync)
8.  PlayerWebSocketController (depende de PlayQueueService + SimpMessagingTemplate)
9.  PlayHistoryService        (añadir recordIfSignificant)
10. ScannerWsBridge + hook en MusicScannerService/LibrarySyncService
11. Tests (6)
```

---

## 3. Checkpoints de test

| Checkpoint | Cuándo | Comando |
|------------|--------|---------|
| A | Tras pasos 1-2 | `./mvnw -q -Dtest=StreamServiceTest,StreamControllerTest test` |
| B | Tras pasos 3-6 | `./mvnw -q -Dtest=WebSocketConfigTest,PlayerWebSocketControllerTest,PlayQueueServiceTest test` |
| C | Tras paso 7 | `./mvnw -q -Dtest=PlayHistoryServiceTest test` |
| D | Final | `./mvnw clean compile` + `./mvnw clean test` |

---

## 4. Tests (mínimo 5 clases, objetivo 6+)

| Clase | Tipo | Cubre |
|-------|------|-------|
| `StreamServiceTest` | unit (mock FS / tmp) | Range start=0, start=mid, end>EOF (clamp), start– sin fin, 416, MIME, 404 archivo/entidad |
| `StreamControllerTest` | `@WebMvcTest` | 206 + Content-Range/Accept-Ranges, 200 full file, 416, 404, 401 sin token |
| `WebSocketConfigTest` | context config | endpoint `/ws`, broker `/topic`/`/queue`, prefijo `/app` registrados |
| `PlayerWebSocketControllerTest` | unit (mock SimpMessagingTemplate) | PLAY/PAUSE/SEEK/NEXT/PREV, queue update, broadcasts emitidos |
| `PlayQueueServiceTest` | unit (mock repos) | advance (NONE/ALL/ONE), retreat, shuffle, syncCurrentTrack, getOrderList |
| `PlayHistoryServiceTest` | unit (mock repos) | recordIfSignificant: >30s, >50%, completed=true, <umbral no registra |

---

## 5. Riesgos

| Riesgo | Impacto | Mitigación |
|--------|---------|------------|
| Auth JWT en STOMP frágil (no se inyecta `Principal` en `@MessageMapping`) | Alto | Interceptor CONNECT + handshake handler; fallback documentado a WS sin auth (anotar en review, endurecer Fase 9) |
| Archivos grandes → carga en memoria | Alto | `FileChannel`/`ResourceRegion` streaming al `OutputStream`, nunca `readAllBytes` |
| Streams concurrentes (seek spam, multi-cliente) | Medio | IO read-only, sin estado compartido por request; Spring maneja response |
| Desconexiones WS / reconnection | Medio | SockJS + re-emisión idempotente de `PLAYER_SYNC` (cliente Fase 7) |
| `Content-Length` erróneo rompe seek | Alto | `Files.size` real, clamp de `end`, 416 correcto en `start>=size` |
| Scanner → broadcast acopla librería a WS | Bajo | `ScannerWsBridge @Autowired(required=false)`; no rompe arranque si STOMP inactivo |
| H2 (tests) no soporta algunos tipos PostgreSQL | Bajo | Stream usa FS, no DB; WS puro; ya se probó en Fase 2 |

---

## 6. Criterios de salida

- `./mvnw clean compile` PASS.
- `./mvnw clean test` PASS (todos los tests, incl. 6 nuevas clases).
- Range: 206 con `Content-Range`/`Accept-Ranges` correctos; seek HTML5 viable.
- Endpoints WS `/ws` registrados; mensajes WS solo DTOs.
- Review documentada en `docs/reviews/fase-3-streaming-review.md` + push.

---

*Última actualización: 2026-08-06*
