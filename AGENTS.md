# AGENTS.md — NeonVibe

> Fuente de verdad para subagentes de código. Si tienes duda, consulta este archivo antes de preguntar.

---

## 1. Identidad del Proyecto

- **Nombre:** NeonVibe
- **Tipo:** Servidor de música personal (self-hosted)
- **Inspiración visual:** Spotify + cyberpunk neón (retrowave/cyberpunk)
- **Objetivo:** Reproductor web/mobile-first con playlist, carátulas, letras, scrobbling y radio por similitud.
- **Estado:** v0.2 desplegada en producción (Debian Trixie bare-metal) y endurecida
  con una pasada de seguridad, concurrencia, accesibilidad y rendimiento. Incluye
  **rotación/revocación de refresh token**, **UI de scanner (admin)**, **página de
  historial**, **gestión de descargas** y validación de subida de carátulas.
  v0.3 cerrada: fulltext (pg_trgm), notificaciones nativas (Web Push), quality
  selector (transcodificación AAC) y stats personales. v0.4: audiolibros/podcasts
  y Chromecast (ver §11).
- **Testing/CI (2026-10-02):** backend con **566 tests** (`./mvnw test`) y JaCoCo
  (umbral líneas ≥ 82%, ramas ≥ 63%), incluido un smoke test de PostgreSQL con
  Testcontainers; frontend con **118 tests** (Vitest + Testing Library); CI en
  `.github/workflows/ci.yml`. Ver §14.

### Próximos pasos

1. **Rebuild + deploy del JAR** con todos los fixes y features:
   `VITE_GOOGLE_CLIENT_ID="<client-id>.apps.googleusercontent.com" ./deploy/build.sh`
   → `scp dist/neonvibe.jar <host>:/tmp/` → `sudo /tmp/deploy.sh /tmp/neonvibe.jar`.
2. **Verificar en producción:** login de Google, escaneo real de `/srv/Music`
   (`POST /api/v1/admin/scan`), scrobbling real con claves Last.fm, y el refresh de
   token al expirar el access token (15 min).
3. **v0.3 cerrada.** Planificar v0.4 (roadmap en §11): audiolibros/podcasts y
   Chromecast.

---

## 2. Stack Tecnológico

### Backend
- **Lenguaje:** Java 21 (LTS)
- **Framework:** Spring Boot 3.4.x (actual: 3.4.13)
- **Build:** Maven
- **Base de datos:** PostgreSQL 15+
- **Mensajería en tiempo real:** WebSockets (STOMP sobre SockJS o Spring WebSocket nativo)
- **Auth:** OAuth2 (Google) + JWT interno para sesiones
- **Mapeo DB:** Spring Data JPA + Hibernate
- **Migrations:** Flyway
- **API:** REST JSON + WebSocket events
- **Transcodificación:** FFmpeg on-demand a AAC (caché LRU; opcional, degrada a original)
- **Notificaciones:** Web Push VAPID (librería `nl.martijndwars:web-push`)

### Frontend
- **Runtime:** Node.js (LTS)
- **Package manager:** pnpm
- **Bundler:** Vite
- **Framework:** React 18+ (TypeScript obligatorio)
- **Estilos:** Tailwind CSS + CSS Modules para componentes específicos
- **Estado global:** Zustand (ligero, sin boilerplate)
- **Query/Cache:** TanStack Query (React Query)
- **Router:** React Router v6
- **Reproductor:** Howler.js o ReactPlayer con backend de AudioContext para visualizador
- **PWA:** Service Worker + manifest para mobile (instalable)

### Infraestructura / Servidor
- **Deploy:** JAR ejecutable (`java -jar`)
- **Servicio:** systemd service en Debian Trixie
- **Proxy:** Cloudflare (dominio ya disponible)
- **Storage:** Filesystem local para carátulas cache y música
- **Scanner:** inotify / WatchService para detección real-time de archivos

---

## 3. Arquitectura General

```
┌─────────────┐      ┌──────────────┐      ┌─────────────┐
│   React     │◄────►│ Spring Boot  │◄────►│ PostgreSQL  │
│   (Vite)    │ WS/REST│   (Java 21)  │      │   (Flyway)  │
└─────────────┘      └──────────────┘      └─────────────┘
                            │
                     ┌──────┴──────┐
                     │  Music Dir  │
                     │  (50GB MP3+)│
                     └─────────────┘
```

### Decisiones arquitectónicas clave
1. **Monorepo simple:** backend/ y frontend/ en la misma raíz. Sin Lerna/Nx para no over-engineering.
2. **API first:** Toda funcionalidad expuesta vía REST; WebSocket solo para sync de reproductor y notificaciones.
3. **Scanner async:** Proceso independiente (thread pool) que escucha cambios en filesystem y actualiza metadatos sin bloquear requests.
4. **Transcodificación opcional (futuro):** FFmpeg para convertir bitrate on-the-fly, no en MVP.
5. **Carátulas:** Descarga async vía APIs externas, cache en filesystem, referencia en DB.

---

## 4. Estructura de Carpetas

```
neonvibe/
├── AGENTS.md              # Este archivo
├── README.md              # Documentación humana
├── backend/
│   ├── pom.xml
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/neonvibe/
│   │   │   │   ├── NeonVibeApplication.java
│   │   │   │   ├── config/          # WebSocket, Security, CORS
│   │   │   │   ├── controller/      # REST controllers
│   │   │   │   ├── service/         # Lógica de negocio
│   │   │   │   ├── repository/      # Spring Data JPA
│   │   │   │   ├── domain/          # Entidades JPA
│   │   │   │   ├── dto/             # Request/Response objects
│   │   │   │   ├── mapper/          # MapStruct (preferido) o manual
│   │   │   │   ├── security/        # JWT, OAuth2 handlers
│   │   │   │   ├── scanner/         # File watcher + metadata extractor
│   │   │   │   ├── websocket/       # STOMP handlers
│   │   │   │   └── infra/           # Carátulas, lyrics, last.fm clients
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       ├── application-prod.yml
│   │   │       └── db/migration/    # Flyway scripts
│   │   └── test/
│   └── target/
├── frontend/
│   ├── package.json
│   ├── pnpm-lock.yaml
│   ├── vite.config.ts
│   ├── tsconfig.json
│   ├── index.html
│   └── src/
│       ├── main.tsx
│       ├── App.tsx
│       ├── components/      # UI reusable
│       ├── pages/           # Vistas (Home, Library, History, Downloads, Settings)
│       ├── hooks/           # Custom hooks
│       ├── stores/          # Zustand stores (auth, player, theme, favorites)
│       ├── player/          # Motor de audio, sync STOMP, MediaSession
│       ├── offline/         # IndexedDB + estado de descargas
│       ├── api/             # Cliente axios + TanStack Query wrappers
│       ├── types/           # TypeScript interfaces
│       └── utils/           # Helpers
└── docs/                    # Arquitectura, despliegue, API y planes históricos
```

---

## 5. Modelo de Datos (MVP)

> Definido por las migraciones Flyway (`backend/src/main/resources/db/migration`).
> Convenciones: snake_case, claves `id` de tipo `BIGSERIAL` salvo indicación.

### Entidades principales

**User**
- id (UUID), email, name, avatar_url, google_id, lastfm_session_key, lastfm_username,
  token_version (revocación de refresh tokens), created_at, updated_at

**UserSettings**
- user_id (UUID, PK), theme, cover_sources, notifications_enabled, scrobble_enabled, updated_at

**Track**
- id, file_path (único), title, artist, album, album_artist, year, genre, track_number, disc_number, duration_seconds, bitrate, format, mime_type, has_lyrics, is_available, cover_art_path, album_id, artist_id, created_at, updated_at
- Índices: B-tree (artist, album, genre, title) + GIN `pg_trgm` (V10) sobre `lower(title/artist/album)` para búsqueda "contains"

**Album**
- id, name, artist, year, genre, cover_art_path, cover_fetched_at, created_at
- Unique: (name, artist)

**Artist**
- id, name (único), cover_art_path, cover_fetched_at, created_at

**Playlist**
- id, user_id, name, description, is_public, cover_art_path, created_at, updated_at

**PlaylistTrack**
- id, playlist_id, track_id, position
- Unique: (playlist_id, track_id)

**PlayQueue**
- id, user_id (único), current_track_id, position_seconds, shuffle_enabled, repeat_mode (enum ALL/NONE/ONE), tracks_order (texto JSON), version (optimistic locking), updated_at

**PlayHistory**
- id, user_id, track_id, played_at, completed, duration_listened_seconds

**Favorite**
- id, user_id, entity_type (enum ALBUM/ARTIST/TRACK), entity_id, created_at
- Unique: (user_id, entity_type, entity_id)

**PushSubscription**
- id, user_id, endpoint (único), p256dh, auth, created_at
- Una fila por dispositivo/navegador suscrito a notificaciones Web Push (V11)

---

## 6. Endpoints API (REST)

Base: `/api/v1`

Auth:
- `POST /auth/google` — Login con token de Google
- `POST /auth/refresh` — Refresh JWT (rota el refresh token; valida `token_version`)
- `POST /auth/logout` — Revoca los refresh tokens del usuario (sube `token_version`)
- `GET /auth/me` — Usuario actual

Tracks:
- `GET /tracks` — Listado paginado (query: q, artist, album, genre, year)
- `GET /tracks/:id` — Detalle
- `GET /tracks/:id/stream` — Stream del archivo (range requests obligatorio). Acepta `?quality=original|high|normal|data` (transcodificación AAC cacheada on-demand)
- `GET /tracks/:id/lyrics` — Letras
- `GET /tracks/:id/cover` — Carátula (redirect a cache o generar)
- `PUT /tracks/:id/metadata` — Edita los tags y **los escribe en el fichero
  real** (jaudiotagger); solo admin

Albums:
- `GET /albums`
- `GET /albums/:id`
- `GET /albums/:id/tracks`
- `GET /albums/:id/cover`
- `POST /albums/:id/cover` — Upload manual (multipart, **solo admin**; valida PNG/JPEG/WebP por magic bytes, rechaza SVG)
- `PUT /albums/:id/metadata` — Edita nombre/año/género y lo propaga a los tags de
  todos los ficheros del álbum (solo admin)

Artists:
- `GET /artists`
- `GET /artists/:id`
- `GET /artists/:id/albums`
- `GET /artists/:id/tracks`
- `GET /artists/:id/cover`
- `POST /artists/:id/cover` — Upload manual (multipart, **solo admin**; misma validación)
- `PUT /artists/:id/metadata` — Renombra el artista y lo propaga a sus canciones y
  álbumes (solo admin)

Playlists:
- `GET /playlists` — Mis playlists + públicas
- `POST /playlists`
- `GET /playlists/:id`
- `PUT /playlists/:id`
- `DELETE /playlists/:id`
- `POST /playlists/:id/tracks` — Añadir track
- `DELETE /playlists/:id/tracks/:trackId`
- `POST /playlists/:id/reorder`

Público (sin auth):
- `GET /public/playlists/:id` — Vista read-only de una playlist pública

Queue:
- `GET /queue` — Cola actual
- `PUT /queue` — Actualizar cola completa

History:
- `GET /history` — Paginado, enriquecido con título/artista/álbum del track
- `POST /history` — Registrar reproducción

Favorites:
- `GET /favorites` — Todos
- `GET /favorites/tracks` — Solo tracks
- `GET /favorites/albums` — Solo álbumes
- `GET /favorites/artists` — Solo artistas
- `POST /favorites`
- `DELETE /favorites/:id`

Radio:
- `GET /radio/seed?track_id=` — Generar playlist de similitud

Settings:
- `GET /settings`
- `PUT /settings`
- `POST /settings/cache/clear`

Last.fm:
- `GET /lastfm/auth-url` — URL de autorización OAuth
- `GET /lastfm/callback` — Callback de conexión
- `POST /lastfm/disconnect`

Scanner (solo admin, vía `ADMIN_EMAILS`):
- `POST /admin/scan` — Trigger manual scan (202)
- `GET /admin/scan/status` — Estado del scanner
- `POST /admin/upload` — Sube canciones o un ZIP (multipart `files`); se escriben
  en staging, se organizan en `<Artista>/<Álbum>/` y se ingieren

Stats (por usuario):
- `GET /stats/overview?range=` — Totales (tiempo, plays, completadas) y distintos
- `GET /stats/top?type=&range=&limit=` — Top tracks/albums/artists/genres
- `GET /stats/timeline?range=&bucket=&tz=` — Actividad por día/semana/mes
- `GET /stats/hours?range=&tz=` — Reproducciones por hora del día

Transcodificación (quality selector):
- `GET /transcode/status` — Si FFmpeg está disponible + presets soportados

Notificaciones nativas (Web Push):
- `GET /push/public-key` — Clave VAPID pública + `configured`
- `POST /push/subscribe` — Registra una suscripción del navegador
- `POST /push/unsubscribe` — Elimina una suscripción
- `POST /push/test` — Notificación de prueba (503 si no hay VAPID)

---

## 7. WebSocket Events

Endpoint: `/ws` (STOMP sobre SockJS, ver `config/WebSocketConfig.java`).

- Prefijo app (cliente → servidor): `/app`
- Prefijo broker (servidor → cliente): `/topic`

Eventos cliente → servidor (`@MessageMapping`):
- `/player/play`, `/player/pause`, `/player/seek`, `/player/next`, `/player/prev` — Acciones de control
- `/queue/update` — Modificar cola

Eventos servidor → cliente:
- `/topic/sync/{userId}` — `PLAYER_SYNC` (estado del reproductor: track, posición, playing/paused) y `QUEUE_UPDATED` (la cola cambió)
- `/topic/admin/scanner` — `SCANNER_PROGRESS` (progreso de escaneo) y `NEW_TRACKS` (nuevas canciones detectadas)

Todos los mensajes llevan un `originator` (id de cliente) para que cada pestaña
ignore sus propios ecos. Ver `websocket/PlayerWebSocketController.java` y
`websocket/ScannerWsBridge.java`.

---

## 8. Reglas de Código

### Backend (Java)
- **Package base:** `com.neonvibe`
- **No expongas entidades JPA directamente.** Usa DTOs.
- **Preferir constructor injection.** No `@Autowired` en campos.
- **Manejo de excepciones:** `@ControllerAdvice` global. Errores en JSON estandarizado: `{error, message, timestamp}`
- **Async:** Usar `@Async` para scanner y descarga de carátulas.
- **Seguridad:** Rutas `/api/**` protegidas excepto `/auth/**`. CORS configurado para el dominio de frontend.
- **Resources estáticos:** Servir desde `classpath:/static` en dev; en prod, el frontend se sirve desde `src/main/resources/static` (build integrado) o proxy separado (decidir en deploy).

### Frontend (React + TS)
- **Funcional components + hooks.** No class components.
- **TypeScript estricto.** No `any` sin justificación documentada.
- **Rutas:** `/`, `/home`, `/library`, `/albums`, `/album/:id`, `/artists`, `/artist/:id`, `/playlists`, `/playlist/:id`, `/favorites`, `/history`, `/downloads`, `/upload`, `/search`, `/settings`, `/p/:id`
- **Mobile-first.** Diseñar para pantallas <400px, escalar a desktop.
- **PWA:** `manifest.json`, service worker mínimo para cache de assets.
- **Visualizador:** Web Audio API + Canvas 2D. Fallback si no hay soporte.
- **Reproductor:** Barra fija en mobile (bottom). En desktop puede ser sidebar o bottom.
- **Tema:** Tailwind config con `dark` (default) y `light`. Paleta neón definida en CSS variables:
  - `--neon-cyan: #00f3ff`
  - `--neon-pink: #ff00ff`
  - `--neon-purple: #bc13fe`
  - `--neon-yellow: #faff00`

### Base de Datos
- **Nombres:** snake_case para tablas y columnas.
- **Flyway:** Scripts versionados `V1__init.sql`, `V2__add_lyrics.sql`, etc.
- **Índices:** Búsqueda case-insensitive "contains" con `pg_trgm` + índices **GIN**
  (`lower(col) gin_trgm_ops`, migración V10) sobre títulos, artistas y álbumes;
  el `LIKE '%q%'` usa el índice en lugar de un seq scan.

---

## 9. APIs Externas

| Función | API | Notas |
|---|---|---|
| Carátulas | iTunes, MusicBrainz, Last.fm | Fallback cascade (más artwork embebido y placeholder) |
| Letras | LRCLIB | Cache local (Genius no implementado) |
| Scrobbling | Last.fm API | Configurable por usuario |
| Auth | Google Identity Services + verificación `id_token` | No se usa `spring-security-oauth2-client`; se valida el `aud` con `GOOGLE_CLIENT_ID` |

---

## 10. Configuración (application.yml)

```yaml
neonvibe:
  jwt:
    secret: ${JWT_SECRET}
    access-token-expiration-ms: 900000        # 15 minutos
    refresh-token-expiration-ms: 604800000    # 7 días
  auth:
    google:
      enabled: ${GOOGLE_AUTH_ENABLED:true}
      tokeninfo-url: https://oauth2.googleapis.com/tokeninfo
      client-id: ${GOOGLE_CLIENT_ID}
    allowed-emails: ${ALLOWED_EMAILS}
  # Allowlist de administradores (scanner, subida de carátulas). Obligatorio en prod.
  admin-emails: ${ADMIN_EMAILS:}
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS}
  music:
    paths: ${MUSIC_PATHS:/srv/Music}          # 50GB de MP3+ en el servidor
    supported-formats: mp3,flac,aac,ogg,m4a,wav
    scan-interval-seconds: ${SCAN_INTERVAL:0} # 0 = solo watcher real-time/manual
    # Web uploads (staging, fuera de la biblioteca) y carpeta de entrada vigilada.
    staging-path: ${MUSIC_STAGING:}           # vacío => <java.io.tmpdir>/neonvibe-staging
    incoming-path: ${MUSIC_INCOMING:}         # vacío => <primer root>/incoming
    incoming-scan-interval-seconds: ${MUSIC_INCOMING_INTERVAL:60} # barrido periódico (0 = off)
  covers:
    cache-path: ${COVERS_CACHE:./data/covers}
    max-size-mb: 500
    retry-days: 7
  lyrics:
    cache-path: ${LYRICS_CACHE:./data/lyrics}
  lastfm:
    api-key: ${LASTFM_API_KEY:}
    api-secret: ${LASTFM_API_SECRET:}
  # Web Push nativo. Claves VAPID en base64url (vacías = función deshabilitada).
  # Generar: npx web-push generate-vapid-keys
  push:
    public-key: ${VAPID_PUBLIC_KEY:}
    private-key: ${VAPID_PRIVATE_KEY:}
    subject: ${VAPID_SUBJECT:mailto:admin@neonvibe.local}
  # Transcodificación on-demand a AAC (quality selector). Requiere FFmpeg; si no
  # está, se sirve el original.
  transcode:
    enabled: ${TRANSCODE_ENABLED:true}
    ffmpeg-path: ${FFMPEG_PATH:ffmpeg}
    cache-path: ${TRANSCODE_CACHE:./data/transcode}
    max-cache-mb: ${TRANSCODE_MAX_CACHE_MB:2048}
    max-concurrent: ${TRANSCODE_MAX_CONCURRENT:1}
    timeout-seconds: ${TRANSCODE_TIMEOUT:300}
  frontend-base: ${FRONTEND_BASE}
  websocket:
    allowed-origins: ${WS_ORIGINS:"*"}        # Restringir en prod
```

En `prod` todos los secretos vienen de variables de entorno cargadas por systemd
desde `/opt/neonvibe/neonvibe.env` (ver `docs/DEPLOY.md`).

---

## 11. Checklist de Features (MVP vs Futuro)

### MVP (v0.1)
- [x] Auth Google + JWT
- [x] Scanner real-time (WatchService/Java inotify)
- [x] Playback streaming (HTTP range + Web Audio)
- [x] Playlists CRUD
- [x] Cola persistente + historial
- [x] Shuffle, repeat, crossfade (crossfade frontend-only ok)
- [x] Carátulas (descarga automática + cache)
- [x] Letras (LRCLIB)
- [x] Favoritos
- [x] Búsqueda básica
- [x] PWA instalable
- [x] Mobile responsive
- [x] Visualizador básico
- [x] Modo oscuro/claro

### v0.2
- [x] Last.fm scrobbling
- [x] Radio por similitud
- [x] WebSocket sync multi-device
- [x] Compartir playlists
- [x] Offline download (IndexedDB/cache) + gestión de descargas
- [x] Background playback (via PWA/MediaSession API)
- [x] Refresh token con rotación y revocación
- [x] UI de scanner (admin) y página de historial
- [x] Transcodificación / quality selector (se traslada a v0.3)
- [x] Notificaciones nativas (se traslada a v0.3)

### v0.3 (cerrada)
- [x] Transcodificación / quality selector (cache-transcode AAC)
- [x] Notificaciones nativas (Web Push + VAPID)
- [x] Búsqueda avanzada (fulltext PostgreSQL — `pg_trgm` + GIN, V10)
- [x] Stats y analytics personales

### v0.4 (en curso)
- [x] Edición de metadata desde la UI (pistas/álbumes/artistas), escribiendo en los ficheros
- [x] Subida de álbumes/canciones (web upload + carpeta de entrada vigilada)
- [ ] Audiolibros / podcasts
- [ ] Chromecast / Bluetooth audio routing
- Social **descartado** (proyecto de un solo usuario).

---

## 12. Notas para Subagentes

- **Siempre usa DTOs.** Nunca expongas `@Entity` directamente en controllers.
- **Los endpoints de stream** (`/tracks/:id/stream`) deben soportar HTTP Range Requests para que el reproductor pueda hacer seek.
- **El scanner no debe bloquear** la app. Usar `@Async` o `ExecutorService`.
- **Carátulas:** si no se encuentra online, usar un placeholder SVG con gradiente determinista (no hay imagen de la que extraer color dominante).
- **Frontend mobile:** la barra de reproducción debe estar fija abajo, con altura mínima 64px.
- **Zustand stores separados:** `playerStore`, `authStore`, `themeStore`, `favoritesStore`.
- **Commits:** mensajes en inglés, formato conventional commits: `feat:`, `fix:`, `docs:`, `refactor:`.

---

## 13. Documentación de la API (OpenAPI / springdoc)

- springdoc-openapi está en el classpath y **se empaqueta en el JAR**; lo que se
  deshabilita en `prod` es su exposición HTTP (`springdoc.api-docs.enabled=false`
  y `swagger-ui.enabled=false`, claves de **raíz**, no bajo `spring:`). En `dev`
  la spec vive en `/v3/api-docs` y Swagger UI en `/swagger-ui`.
- Snapshot de referencia commiteado: `docs/api/openapi.yaml` (se regenera desde
  `/v3/api-docs.yaml`). Si cambian endpoints o DTOs, regenerar y actualizar el snapshot.
- Metadata y esquema de seguridad Bearer: `config/OpenApiConfig.java`.

---

## 14. Testing y CI

- **Backend:** `cd backend && ./mvnw test`. JUnit 5 + Mockito + Spring Boot Test.
  Incluye `PostgresMigrationTest` (Testcontainers) que arranca un PostgreSQL real,
  aplica Flyway `V1..V9` y verifica `ddl-auto: validate`; se **salta solo** si no
  hay Docker.
- **Cobertura:** JaCoCo genera `backend/target/site/jacoco` (HTML + CSV/XML) y el
  build **falla** por debajo de **líneas ≥ 82%** y **ramas ≥ 63%**. Para subir el
  umbral, editar `jacoco-maven-plugin` en `backend/pom.xml`.
- **Frontend:** `cd frontend && pnpm test` (Vitest + Testing Library sobre jsdom).
  `pnpm test:coverage` para el reporte. `pnpm build` hace el typecheck (`tsc -b`).
- **Fixtures de audio:** `backend/src/test/resources/audio/` contiene clips MP3
  cortos con tags ID3 y artwork embebido para probar `JAudioTaggerMetadataExtractor`.
- **CI:** `.github/workflows/ci.yml` ejecuta en cada push/PR a `main`:
  `backend` (tests + artefacto JaCoCo), `frontend` (tests + build) y `package`
  (JAR con frontend embebido). `main` debe quedar siempre verde.
- **Reglas:** añadir/actualizar tests con cada cambio; preferir tests unitarios con
  colaboradores mockeados y `@WebMvcTest` para la capa HTTP; usar `@DataJpaTest`
  (H2) para repositorios y reservar Testcontainers para la integración con
  PostgreSQL.

---

*Última actualización: 2026-10-03*
