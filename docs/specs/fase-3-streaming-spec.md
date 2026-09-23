# Fase 3 — Streaming y WebSocket: Especificación Técnica

> Versión: 1.0 — 2026-08-06
> Propietario: Subagente Fase 3 (streaming)
> Estado: Aprobada (base para la implementación)
> Dependencias: Fase 2 (dominio musical, scanner, PlayQueue/PlayHistory)

---

## 1. Objetivo

Servir audio por HTTP con soporte **HTTP Range Requests** para que el reproductor HTML5
pueda hacer seek en cualquier punto de la canción, y montar la base de **WebSocket
(STOMP)** para sincronización del reproductor multi-dispositivo (estado del player,
control, cola y progreso del scanner).

## 2. Alcance

**Incluye:**

- `GET /api/v1/tracks/{id}/stream` con HTTP Range Requests (206 Partial Content),
  MIME type por `format`/`mimeType`, y seek eficiente vía `FileChannel`.
- Configuración STOMP sobre WebSocket: endpoint `/ws`, broker `/topic` + `/queue`,
  prefijo de aplicación `/app`.
- Autenticación WebSocket por JWT (interceptor de CONNECT: query param `?token=` o
  header STOMP `Authorization`).
- Controladores de mensajes STOMP para player (`PLAY`, `PAUSE`, `SEEK`, `NEXT`, `PREV`)
  y cola (`UPDATE_QUEUE`).
- Eventos servidor → cliente: `PLAYER_SYNC`, `QUEUE_UPDATED`, `SCANNER_PROGRESS`,
  `NEW_TRACKS`.
- DTOs de mensajes WebSocket (nunca entidades JPA en el payload).
- `PlayQueueService` enriquecido con lógica de advance/retreat/shuffle/repeat y
  `syncCurrentTrack`.
- `PlayHistoryService` con `record(...)` reforzado + endpoint `POST /history` existente.
- Integración del scanner para emitir `SCANNER_PROGRESS` / `NEW_TRACKS`.
- Tests (mínimo 5 clases): streaming (Range), config WS, player WS controller, queue
  service y history service.

**Excluye:**

- Cualquier código frontend (reproductor, WebSocket client, barra de player) → Fases 4-7.
- Transcodicación / quality selector → v0.2 / Fase 10.
- Scrobbling Last.fm → v0.2.
- Auth WebSocket hardening (rotación de tokens, reconnection backoff avanzado) → Fase 9.

---

## 3. HTTP Streaming con Range Requests

### 3.1 Endpoint

`GET /api/v1/tracks/{id}/stream`

Protegido por el security filter de JWT (igual que el resto de `/api/**`).

### 3.2 Comportamiento

| Caso | Respuesta |
|------|-----------|
| Track no existe o `isAvailable == false` | `404` (JSON estándar) |
| Archivo `filePath` no existe en disco | `404` |
| Sin header `Range` | `200 OK`, body completo, `Accept-Ranges: bytes`, `Content-Type`, `Content-Length` |
| `Range: bytes=start-end` (válido y start < fileSize) | `206 Partial Content`, `Content-Range: bytes start-end/total`, `Accept-Ranges: bytes`, `Content-Length = end-start+1` |
| `Range: bytes=start-` (sin fin, start < fileSize) | `206`, sirve desde `start` hasta EOF |
| `Range` con `start >= fileSize` u otro sintaxis inválida | `416 Range Not Satisfiable` con `Content-Range: bytes */total` |
| `Range: bytes=0-0` | `206`, 1 byte, `Content-Range: bytes 0-0/total` |
| `Range` con `end >= fileSize` | Se recorta a `end = fileSize-1` (clamp) |

Cabeceras comunes en todas las respuestas válidas:
- `Accept-Ranges: bytes`
- `Content-Type` según `Track.mimeType` o derivado de `format`
- `Content-Length` del chunk servido
- `Cache-Control: private, no-transform` (evita proxies que cacheen audio sensible);
  también apto para streaming.

### 3.3 Implementación (servicio)

Contrato del servicio (`StreamService`):

```java
StreamResult streamFile(Long trackId, HttpHeaders requestHeaders);
```

Donde `StreamResult` expone:
- `FileChannelFileRegion`/`ResourceRegion` (o el chunk) a escribir en la respuesta,
- `HttpStatus` (OK/ PARTIAL_CONTENT),
- cabeceras `Content-Range`, `Content-Length`, `Content-Type`.

Estrategia: abrir `FileChannel` (o `RandomAccessFile`) sobre `filePath`, _seek_ a `start`,
leer `length` bytes. Se usa `org.springframework.core.io.support.ResourceRegion` o
`FileSystemResource` + `InputStreamResource` con `FileRegion` de Netty (opcional); la
opción elegida en el build es **`ResourceRegion` + `FileSystemResource` relativo al
`workspace`** con `org.springframework.http.ResponseEntity` que Spring escribe
directamente al `ServletOutputStream`, evitando cargar el archivo completo en memoria.

Edge cases cubiertos por tests: `start=0`, `start=mid`, `end` pasado de EOF, `start-`
sin fin, `Range` no parseable → 416, y archivo inexistente → 404.

### 3.4 Determinación de MIME type

Resolver en el servicio, con prioridad `Track.mimeType` → derivar de `Track.format` →
`application/octet-stream` como fallback:

| Extensión | MIME |
|-----------|------|
| mp3 | `audio/mpeg` |
| flac | `audio/flac` |
| ogg | `audio/ogg` |
| m4a | `audio/mp4` |
| aac | `audio/aac` |
| wav | `audio/wav` |

Si `Track.mimeType` es nulo pero `format` está poblado, se deriva con `MimeTypeUtils`
o un mapa local. El controller devuelve el `Content-Type` de la respuesta basándose en
este valor.

---

## 4. Arquitectura WebSocket (STOMP)

### 4.1 Decisión: STOMP sobre WebSocket

Se elige **STOMP sobre WebSocket** (Spring `@MessageMapping` / `SimpMessagingTemplate`)
en lugar de raw WebSocket porque:

- Spring STOMP abstrae tópicos, destino (`/topic`, `/user`), y manejo de sesiones.
- El frontend (Fases 7) consume STOMP cómodamente con `@stomp/stompjs`.
- El `SimpMessagingTemplate` permite broadcasts tipo `PLAYER_SYNC`/`SCANNER_PROGRESS`
  de forma declarativa.
- Con SockJS como fallback se mantiene compatibilidad con proxies que no soportan
  WebSocket nativo.

### 4.2 Configuración (`WebSocketConfig`)

| Parámetro | Valor |
|-----------|-------|
| Endpoint | `/ws` (con `.setAllowedOriginPatterns("*")` y SockJS activo) |
| Simple broker | `/topic` (broadcast) y `/queue` (por usuario) |
| Application prefix | `/app` |
| User destination prefix | `/user` |
| CORS | `neonvibe.websocket.allowed-origins` de `application.yml` (default `*`, restringir en prod) |

Registro del interceptor de autenticación en `clientInboundChannel`.

### 4.3 Autenticación JWT en WebSocket

`WebSocketAuthInterceptor` implementa `ChannelInterceptor`:
- Reacciona a `StompCommand.CONNECT` (o `SUBSCRIBE` como fallback).
- Extrae el token de: (a) query param `?token=` en la URL de conexión, o (b) header
  STOMP `Authorization: Bearer ...`.
- Valida con `JwtTokenProvider.validateAccessToken`.
- Crea un `UserPrincipal(userId, email, name)` y lo coloca en
  `accessor.getSessionAttributes().put("SPRING_SECURITY_CONTEXT", ...)` o directamente
  como `UserPrincipal` en los atributos de sesión.
- El listener `PrincipalHandshakeHandler` (o un `DefaultHandshakeHandler` adaptado)
  asegura que las sesiones con principal valido inyectan ese principal en STOMP,
  permitiendo `@MessageMapping` recibir el `Principal` autenticado.

**Nota de riesgo (documentada):** el pipeline STOMP/JWT con `@MessageMapping` puede ser
frágil. Si falla el wiring del principal en el handshake, el fallback documentado es:
mantener la conexión WebSocket **sin auth** para el MVP (validando el token solo en el
endpoint REST de stream) y anotarlo como "auth WebSocket a endurecer en Fase 9". La
implementación de esta fase prioriza un interceptor que valida el token en CONNECT y
guarda un `UserPrincipal` en los atributos de sesión.

### 4.4 Tópicos y formatos de mensaje

Canal base (broadcast): `/topic/sync/{userId}`.
Canal por usuario: `/user/{userId}/queue/sync`.

#### Servidor → cliente

| Evento | Tópico | Payload (DTO) |
|--------|--------|---------------|
| `PLAYER_SYNC` | `/topic/sync/{userId}` | `PlayerSyncMessage` |
| `QUEUE_UPDATED` | `/topic/sync/{userId}` | `QueueUpdateMessage` |
| `SCANNER_PROGRESS` | `/topic/admin/scanner` (broadcast) | `ScannerProgressMessage` |
| `NEW_TRACKS` | `/topic/admin/scanner` | `NewTracksMessage` |

#### Cliente → servidor (`@MessageMapping`)

| Destino | DTO de entrada | Acción |
|---------|----------------|--------|
| `/app/player/play` | `PlayerActionMessage(PLAY, position)` | Marca current track, broadcast `PLAYER_SYNC` |
| `/app/player/pause` | `PlayerActionMessage(PAUSE, position)` | broadcast `PLAYER_SYNC` isPlaying=false |
| `/app/player/seek` | `PlayerActionMessage(SEEK, position)` | persiste posición, broadcast |
| `/app/player/next` | `PlayerActionMessage(NEXT)` | `advanceTrack`, broadcast `PLAYER_SYNC` + `QUEUE_UPDATED` |
| `/app/player/prev` | `PlayerActionMessage(PREV)` | `retreatTrack`, broadcast |
| `/app/queue/update` | `QueueUpdateRequest` (tracks[]) | `updateQueue`, broadcast `QUEUE_UPDATED` |

### 4.5 DTOs WebSocket (paquete `com.neonvibe.websocket.dto`)

```java
record PlayerActionMessage(String action, Integer positionSeconds) {}

record PlayerSyncMessage(Long trackId, Integer positionSeconds, boolean isPlaying,
                        long timestamp, Long queueId) {}

record QueueUpdateMessage(UUID userId, List<Long> tracksOrder, Long currentTrackId) {}

record ScannerProgressMessage(int scannedCount, int totalCount, String status) {}

record NewTracksMessage(List<Long> trackIds, int addedCount) {}
```

`String` constants para acciones: `PLAY`, `PAUSE`, `SEEK`, `NEXT`, `PREV`.

---

## 5. PlayQueue Service (enriquecimiento)

Extender `PlayQueueService` con métodos de dominio (además de los ya existentes
`getForUser` / `saveForUser`):

```java
List<Long> getOrderList(UUID userId);                  // decodifica tracksOrder
PlayQueueResponse updateQueue(UUID userId, List<Long> tracksOrder, Long currentTrackId);
PlayQueue advanceTrack(UUID userId);                    // next, honra repeat/shuffle
PlayQueue retreatTrack(UUID userId);                    // prev
PlayQueue syncCurrentTrack(UUID userId, Long trackId, Integer position);
```

### 5.1 Lógica advance/retreat

- La cola es la lista ordenada `tracksOrder`.
- `shuffleEnabled=true` → `advanceTrack` selecciona un índice aleatorio restante;
  para el MVP, con `shuffle`, se permuta `tracksOrder` al activarse y se recorre en orden
  permutado (comportamiento determinista y simple).
- `RepeatMode`:
  - `NONE`: al llegar al final, no avanza (se queda o se detiene).
  - `ALL`: al llegar al final, vuelve al inicio.
  - `ONE`: repite el mismo track.
- `retreatTrack`: retrocede un índice; en `NONE`, si está en el primero, se queda.

### 5.2 Persistencia

Cada operación persiste `currentTrackId`, `positionSeconds` y `tracksOrder` y devuelve
la entidad/dto actualizada, lista para que el controller WebSocket emita broadcasts.

---

## 6. PlayHistory Recording

`PlayHistoryService.record(UUID userId, PlayHistoryRequest request)` ya existe y guarda
`completed` + `durationListenedSeconds`.

Regla de registro (documentada, delegada al cliente/WS en esta fase):
- Registrar al terminar el track (**completed=true**).
- Registrar en "next"/"stop" si se escuchó **>50%** de la duración (completed=false con
  `durationListenedSeconds`).
- Registrar en stop explícito tras **>30 segundos**.

Para el MVP, el trigger real lo emite el cliente: al hacer `NEXT`/`STOP`, el frontend
(enviador) o el backend (cuando `advanceTrack` detecta cambio de track) llama a
`record`. **Implementación backend de esta fase:** exponer `record(...)` y añadir un
helper `recordIfSignificant(userId, trackId, positionSeconds)` que, si
`positionSeconds > 30` (o >50% de `durationSeconds`), persiste historial. El wiring
completo cliente→backend se hace en Fase 7.

---

## 7. Integración con el Scanner

En esta fase se deja un **puente opcional** (`ScannerWsBridge`) que usa
`SimpMessagingTemplate` para emitir:
- `SCANNER_PROGRESS` con `ScannerStatus` (scanned/total/status) al final de
  `scanAll()` o en ticks de progreso.
- `NEW_TRACKS` con los ids de tracks nuevos detectados durante un `upsert`.

Wiring: si `LibrarySyncService`/`MusicScannerService` reportan tracks nuevos, se
publica `NewTracksMessage`. Para evitar acoplar el scanner a WebSocket, el bridge se
inyecta de forma opcional (`@Autowired(required=false)`) y no rompe el arranque si el
STOMP no está activo. Detalle fino del progreso se deja para Fase 8/10 (progreso
incremental por archivo); aquí se publican eventos básicos al completar escaneos.

---

## 8. Archivos a crear / modificar

### Crear (main)

| Archivo | Propósito |
|---------|-----------|
| `config/WebSocketConfig.java` | Endpoint `/ws`, broker `/topic`/`/queue`, prefijo `/app`, CORS, registro interceptor |
| `websocket/WebSocketAuthInterceptor.java` | Valida JWT en CONNECT, inyecta `UserPrincipal` |
| `websocket/UserPrincipalHandshakeHandler.java` | Asegura principal en sesión STOMP |
| `websocket/dto/PlayerActionMessage.java` | DTO entrada de acciones |
| `websocket/dto/PlayerSyncMessage.java` | DTO broadcast player |
| `websocket/dto/QueueUpdateMessage.java` | DTO broadcast cola |
| `websocket/dto/ScannerProgressMessage.java` | DTO broadcast scanner |
| `websocket/dto/NewTracksMessage.java` | DTO broadcast tracks nuevos |
| `websocket/PlayerWebSocketController.java` | `@MessageMapping` player + queue |
| `websocket/ScannerWsBridge.java` | Publica `SCANNER_PROGRESS`/`NEW_TRACKS` |
| `service/StreamService.java` | Lógica Range Requests + MIME |
| `controller/StreamController.java` | `GET /tracks/{id}/stream` |
| `infra/FileStorageResolver.java` (o en `StreamService`) | Valida/abre `filePath` |

### Modificar

| Archivo | Cambio |
|---------|--------|
| `service/PlayQueueService.java` | Añadir `updateQueue`, `advanceTrack`, `retreatTrack`, `syncCurrentTrack`, getters de order |
| `service/PlayHistoryService.java` | Añadir `recordIfSignificant(...)` |
| `scanner/LibrarySyncService.java` | Reportar ids nuevos / contadores para `NEW_TRACKS` (puente opcional) |
| `scanner/MusicScannerService.java` | Disparar `ScannerWsBridge.publishProgress()`/`publishNewTracks()` al escanear |
| `config/SecurityConfig.java` | (posible) permitir el handshake `/ws/**` en caso de necesitar bypass parcial |

### Crear (test, mínimo 5 clases)

| Test | Cubre |
|------|-------|
| `StreamServiceTest` | Edge cases de Range, MIME, 404 |
| `StreamControllerTest` | `@WebMvcTest` con mock HTTP servlet: 206, Content-Range, Accept-Ranges |
| `WebSocketConfigTest` | Endpoints/broker registrados (`WebSocketStompEndpointRegistry`) |
| `PlayerWebSocketControllerTest` | `@MessageMapping` con `SimpMessagingTemplate` mockeado |
| `PlayQueueServiceTest` (ext) | advance/retreat/shuffle/repeat/syncCurrentTrack |
| `PlayHistoryServiceTest` | `recordIfSignificant` (>30s, >50%, completed) |
| (integración, opcional) `WebSocketAuthIntegrationTest` | Conexión con JWT query param |

---

## 9. Criterios de aceptación

- Reproductor HTML5 (`<audio>`) puede hacer seek con `206 Partial Content`.
- `GET /tracks/{id}/stream` con `Range` devuelve la porción correcta y cabeceras
  `Content-Range`/`Accept-Ranges`/`Content-Length`.
- `./mvnw clean compile` PASS y `./mvnw test` PASS (todos los tests, >=5 clases nuevas).
- WebSocket endpoint `/ws` registrado; STOMP responde a CONNECT/SUBSCRIBE.
- Los mensajes WS solo transportan DTOs (nunca `@Entity`).
- `PLAYER_SYNC`, `QUEUE_UPDATED`, `SCANNER_PROGRESS`, `NEW_TRACKS` son los eventos
  servidor → cliente emitidos.

---

## 10. Riesgos y mitigaciones

| Riesgo | Mitigación |
|--------|------------|
| Wiring de auth JWT en STOMP frágil (`@MessageMapping` sin principal) | Interceptor de CONNECT + handshake handler; si no compila/rompe, fallback a WS sin auth documentado (Fase 9) |
| Archivos grandes (MP3 50GB librería) → lecturas completas en memoria | `FileChannel`/`ResourceRegion` streaming a `OutputStream`; nunca cargar todo el archivo |
| Streams concurrentes (varios clientes, seek spam) | IO de solo lectura sobre `FileChannel.share`; Spring maneja el write al response; sin estado compartido por request |
| Desconexiones WS (cambios de red, mobiles) | SockJS fallback + reconnect del cliente (Fase 7); el estado del player es idempotente re-emitible (`PLAYER_SYNC`) |
| `Content-Length` incorrecto rompe seek del browser | Siempre derivar de `fileSize` real (`Files.size`), clamp de `end`, y 416 correcto |
| Scanner bloqueando WS al broadcast | Bridge opcional `@Autowired(required=false)` + eventos al finalizar scan (no por byte) |

---

*Última actualización: 2026-08-06*
