# Arquitectura — NeonVibe

> Documento de arquitectura orientado a personas. Define los componentes, los
> flujos clave y las decisiones de diseño del sistema. El contexto operativo para
> agentes de código vive en `AGENTS.md`.

## 1. Resumen

NeonVibe es un servidor de música personal self-hosted. Consiste en un backend
Spring Boot (Java 21) que sirve una API REST JSON, un frontend React servido como
recurso estático embebido en el mismo JAR, y un canal de tiempo real STOMP sobre
WebSocket. Se ejecuta como un único proceso en un servidor Debian Trixie
bare-metal, detrás de Cloudflare, contra PostgreSQL.

La biblioteca de audio se lee directamente del filesystem (`/srv/Music`); no hay
copia ni transcodificación.

## 2. Contexto del sistema

```mermaid
flowchart LR
    Browser[Browser / PWA]
    CF[Cloudflare: DNS, TLS, tunnel]
    Server[Servidor Debian Trixie<br/>Spring Boot JAR]
    DB[(PostgreSQL)]
    Music[(/srv/Music)]
    GIS[Google Identity Services]
    LastFM[Last.fm API]
    LRC[LRCLIB / Genius]
    Covers[MusicBrainz / iTunes]

    Browser -->|HTTPS / WSS| CF
    CF -->|HTTP :8080| Server
    Server --> DB
    Server --> Music
    Browser <--> GIS
    Server <--> GIS
    Server <--> LastFM
    Server --> LRC
    Server --> Covers
```

- El navegador habla únicamente con Cloudflare; el servidor no expone otros
  puertos a Internet.
- El backend valida los `id_token` de Google contra `tokeninfo`; no usa
  `spring-security-oauth2-client` para el login.
- La música se sirve por HTTP Range requests; el frontend hace seek sobre el
  archivo sin transcodificación.

## 3. Componentes

```mermaid
flowchart TD
    subgraph Frontend[Frontend - React 18 + Vite]
        UI[UI: Tailwind, móvil-first]
        RQ[TanStack Query + axios]
        Z[Zustand: player, queue, auth, library]
        WS[STOMP sobre SockJS]
        SW[Service Worker PWA]
    end

    subgraph Backend[Backend - Spring Boot]
        REST[Controllers REST]
        SVC[Services]
        REPO[Spring Data JPA]
        SEC[Security: JWT filter + CORS]
        WSC[WebSocket controllers]
        SCAN[Scanner: WatchService]
        EXT[Clientes externos: covers, lyrics, Last.fm]
    end

    DB[(PostgreSQL)]
    FS[(Filesystem)]

    UI --> RQ
    RQ --> REST
    Z --> WS
    WS --> WSC
    REST --> SEC
    REST --> SVC
    SVC --> REPO
    REPO --> DB
    SVC --> EXT
    SCAN --> REPO
    SCAN --> FS
    WSC --> SVC
    SW --> CF2[Cloudflare]
```

Módulos backend (`com.neonvibe.*`):

| Paquete | Responsabilidad |
|---|---|
| `controller` | Endpoints REST bajo `/api/v1` |
| `service` | Lógica de negocio (auth, librería, cola, radio, ...) |
| `repository` | Acceso a datos Spring Data JPA |
| `security` | `JwtAuthenticationFilter`, emisión/validación de JWT |
| `scanner` | WatchService + extracción de metadatos (jaudiotagger) |
| `websocket` | `@MessageMapping` de reproductor/cola y bridge de scanner |
| `config` | Seguridad, CORS, WebSocket (STOMP), Jackson |
| `infra` | Clientes HTTP: MusicBrainz, Last.fm, LRCLIB |
| `dto`, `domain`, `mapper` | Contratos de API, entidades JPA, mapeo |

## 4. Autenticación (Google + JWT)

```mermaid
sequenceDiagram
    participant B as Browser
    participant GIS as Google Identity Services
    participant S as Spring Boot
    participant G as Google tokeninfo
    participant DB as PostgreSQL

    B->>GIS: solicitud de credenciales
    GIS-->>B: id_token
    B->>S: POST /api/v1/auth/google {id_token}
    S->>G: GET tokeninfo?id_token=...
    G-->>S: claims (email, aud, email_verified)
    S->>S: verifica aud == GOOGLE_CLIENT_ID<br/>y email en ALLOWED_EMAILS
    S->>DB: upsert del usuario
    S-->>B: {access_token, refresh_token}
    B->>S: GET /api/v1/auth/me (Authorization: Bearer)
    S-->>B: datos del usuario
```

Decisiones relevantes:

- **Sesión stateless:** access token de 15 min + refresh de 7 días. El backend no
  guarda sesiones en memoria.
- **Verificación del `aud`:** `tokeninfo` prueba que el token es auténtico, no que
  se emitiera para esta app. Sin este chequeo, un `id_token` de cualquier otra
  web con Google Sign-In serviría para entrar.
- **Allowlist obligatoria en prod:** `ALLOWED_EMAILS`; fuera de la lista se
  responde `403`.
- **`ProdStartupGuard`:** en perfil `prod` la app no arranca sin `JWT_SECRET`,
  `GOOGLE_CLIENT_ID` y `ALLOWED_EMAILS`.

## 5. Streaming (HTTP Range)

```mermaid
sequenceDiagram
    participant B as Browser (Howler / audio)
    participant S as Spring Boot
    participant FS as Filesystem

    B->>S: GET /api/v1/tracks/:id/stream (Range: bytes=0-)
    S->>S: valida JWT y existencia del track
    S->>FS: abre archivo
    S-->>B: 206 Partial Content, audio/mpeg
    B->>S: GET /api/v1/tracks/:id/stream (Range: bytes=32768-)
    S->>FS: seek al offset
    S-->>B: 206 Partial Content (bytes 32768-...)
```

- El endpoint responde `206` y procesa `Range`, lo que habilita el seek del
  reproductor. Sin esto, arrastrar la barra de progreso reinicia la canción.
- El JWT viaja en query param solo en los endpoints de media
  (`/stream`, `/cover`).

## 6. Scanner

```mermaid
sequenceDiagram
    participant W as WatchService
    participant SC as MusicScannerService
    participant DB as PostgreSQL
    participant WS as WebSocket broker

    W-->>SC: evento de filesystem (create / modify / delete)
    SC->>SC: extrae metadatos (jaudiotagger)
    SC->>DB: upsert de Track / Album / Artist
    SC-->>WS: /topic/admin/scanner<br/>(SCANNER_PROGRESS, NEW_TRACKS)
```

- Detección en tiempo real mediante `java.nio.file.WatchService`; además existe
  un trigger manual (`POST /api/v1/admin/scan`).
- El escaneo corre fuera del hilo de requests (`@Async` / executor); no bloquea
  la API.
- Los archivos borrados se marcan `is_available=false` (no se eliminan registros).

## 7. Sync multi-dispositivo (WebSocket)

- Endpoint `/ws` (STOMP sobre SockJS), `GET /ws/info` es el handshake de SockJS.
- Cliente → servidor (`/app`): `player/play`, `player/pause`, `player/seek`,
  `player/next`, `player/prev`, `queue/update`.
- Servidor → cliente: `/topic/sync/{userId}` (`PLAYER_SYNC`, `QUEUE_UPDATED`) y
  `/topic/admin/scanner` (`SCANNER_PROGRESS`, `NEW_TRACKS`).
- Todos los mensajes llevan un `originator` (id de cliente) para que cada pestaña
  ignore su propio eco.

```mermaid
sequenceDiagram
    participant A as Pestaña A
    participant S as Spring Boot (broker)
    participant B as Pestaña B

    A->>S: /app/player/play {originator: A}
    S-->>A: /topic/sync/{user} PLAYER_SYNC
    S-->>B: /topic/sync/{user} PLAYER_SYNC
    Note over B: ignora si originator == propio
```

## 8. Despliegue

```mermaid
flowchart TB
    Dev[Desarrollo<br/>pnpm build + mvn package] --> JAR[dist/neonvibe.jar<br/>frontend embebido]
    JAR -->|scp| Server[Servidor]
    Server --> SVC[systemd: neonvibe]
    SVC --> APP[Spring Boot :8080]
    APP --> PG[(PostgreSQL)]
    APP --> MUS[(/srv/Music)]
    APP --> CACHE[(/var/lib/neonvibe<br/>covers + lyrics)]
    APP -->|proxy| CF[Cloudflare]
    CF --> Browser
```

- Un solo artefacto (JAR con el frontend dentro); no hay Nginx: Cloudflare
  termina TLS y reenvía a `:8080`.
- Despliegue atómico: `deploy/deploy.sh` copia el JAR con `mv` y reinicia el
  servicio, validando el health check.
- El procedimiento completo está en `docs/DEPLOY.md`.

## 9. Decisiones de diseño clave

| Decisión | Alternativa descartada | Razón |
|---|---|---|
| Monorepo simple (`backend/` + `frontend/`) | Nx/Lerna | Sin over-engineering para el tamaño del proyecto |
| API first + WebSocket solo para sync | GraphQL / eventos para todo | Contrato REST simple; WS solo para estado de reproductor |
| Validación `id_token` contra `tokeninfo` | Claves JWK locales | Más simple; trade-off: requiere salida a Internet |
| Scanner con WatchService | Polling periódico | Detección en tiempo real sin coste de barrido |
| Frontend embebido en el JAR | Frontend separado en CDN/Nginx | Un solo artefacto, despliegue trivial |
| Stream por HTTP Range sin transcodificación | FFmpeg on-the-fly | MVP; transcodificar queda para el quality selector |
