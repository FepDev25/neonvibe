# AGENTS.md — NeonVibe

> Fuente de verdad para subagentes de código. Si tienes duda, consulta este archivo antes de preguntar.

---

## 1. Identidad del Proyecto

- **Nombre:** NeonVibe
- **Tipo:** Servidor de música personal (self-hosted)
- **Inspiración visual:** Spotify + cyberpunk neón (retrowave/cyberpunk)
- **Objetivo:** Reproductor web/mobile-first con playlist, carátulas, letras, scrobbling y radio por similitud.
- **Estado:** MVP desplegado en producción (Debian Trixie bare-metal). v0.2 implementado
  salvo transcodificación y notificaciones nativas. El JAR desplegado no incluye
  todavía springdoc/OpenAPI (solo dev, ver §13).

### Próxima sesión: rebuild + deploy, y arranque de v0.3

1. **Rebuild del JAR:** `VITE_GOOGLE_CLIENT_ID="285886976623-3k6e7r74666u0ahpmv8kjmo8me52i3fp.apps.googleusercontent.com" ./deploy/build.sh`
   — incorpora springdoc (swagger solo en dev) y todos los fixes commiteados.
2. **Deploy:** `scp dist/neonvibe.jar ssh.fepdev.app:/tmp/` y luego en el server
   `sudo /tmp/deploy.sh /tmp/neonvibe.jar`.
3. **Verificar en producción:** login de Google, abrir álbum y artista (sin React #310),
   `GET /ws/info` respondiendo 200 (SockJS) y `GET /actuator/health` UP.
4. **Planificar v0.3** (roadmap en §11): definir alcance de quality selector,
   notificaciones nativas, fulltext, social y stats antes de implementar.

---

## 2. Stack Tecnológico

### Backend
- **Lenguaje:** Java 21 (LTS)
- **Framework:** Spring Boot 3.x
- **Build:** Maven
- **Base de datos:** PostgreSQL 15+
- **Mensajería en tiempo real:** WebSockets (STOMP sobre SockJS o Spring WebSocket nativo)
- **Auth:** OAuth2 (Google) + JWT interno para sesiones
- **Mapeo DB:** Spring Data JPA + Hibernate
- **Migrations:** Flyway
- **API:** REST JSON + WebSocket events

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
│       ├── pages/           # Vistas (Home, Library, Player, Settings)
│       ├── hooks/           # Custom hooks
│       ├── stores/          # Zustand stores
│       ├── api/             # TanStack Query + fetch wrappers
│       ├── types/           # TypeScript interfaces
│       ├── styles/          # Tailwind config + globals
│       └── utils/           # Helpers
└── docs/                    # ADRs, diagramas, guías
```

---

## 5. Modelo de Datos (MVP)

> Definido por las migraciones Flyway (`backend/src/main/resources/db/migration`).
> Convenciones: snake_case, claves `id` de tipo `BIGSERIAL` salvo indicación.

### Entidades principales

**User**
- id (UUID), email, name, avatar_url, google_id, lastfm_session_key, lastfm_username, created_at, updated_at

**UserSettings**
- user_id (UUID, PK), theme, cover_sources, notifications_enabled, scrobble_enabled, updated_at

**Track**
- id, file_path (único), title, artist, album, album_artist, year, genre, track_number, disc_number, duration_seconds, bitrate, format, mime_type, has_lyrics, is_available, cover_art_path, album_id, artist_id, created_at, updated_at
- Índices: artist, album, genre, title (B-tree, para filtros y búsqueda LIKE)

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
- id, user_id (único), current_track_id, position_seconds, shuffle_enabled, repeat_mode (enum ALL/NONE/ONE), tracks_order (texto JSON), updated_at

**PlayHistory**
- id, user_id, track_id, played_at, completed, duration_listened_seconds

**Favorite**
- id, user_id, entity_type (enum ALBUM/ARTIST/TRACK), entity_id, created_at
- Unique: (user_id, entity_type, entity_id)

---

## 6. Endpoints API (REST)

Base: `/api/v1`

Auth:
- `POST /auth/google` — Login con token de Google
- `POST /auth/refresh` — Refresh JWT
- `GET /auth/me` — Usuario actual

Tracks:
- `GET /tracks` — Listado paginado (query: q, artist, album, genre, year)
- `GET /tracks/:id` — Detalle
- `GET /tracks/:id/stream` — Stream del archivo (range requests obligatorio)
- `GET /tracks/:id/lyrics` — Letras
- `GET /tracks/:id/cover` — Carátula (redirect a cache o generar)

Albums:
- `GET /albums`
- `GET /albums/:id`
- `GET /albums/:id/tracks`
- `GET /albums/:id/cover`
- `POST /albums/:id/cover` — Upload manual (multipart)

Artists:
- `GET /artists`
- `GET /artists/:id`
- `GET /artists/:id/albums`
- `GET /artists/:id/tracks`
- `GET /artists/:id/cover`
- `POST /artists/:id/cover` — Upload manual (multipart)

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
- `GET /history`
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

Scanner:
- `POST /admin/scan` — Trigger manual scan (admin only)
- `GET /admin/scan/status` — Estado del scanner

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
- **Rutas:** `/`, `/home`, `/library`, `/albums`, `/album/:id`, `/artists`, `/artist/:id`, `/playlists`, `/playlist/:id`, `/favorites`, `/search`, `/settings`, `/p/:id`
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
- **Índices:** Crear índices GIN para búsqueda fulltext en PostgreSQL (futuro, MVP usa LIKE + índices B-tree).

---

## 9. APIs Externas

| Función | API | Notas |
|---|---|---|
| Carátulas | MusicBrainz, Last.fm, iTunes, Spotify (sin auth para imágenes) | Fallback cascade |
| Letras | LRCLIB (primaria), Genius (fallback) | Cache local |
| Scrobbling | Last.fm API | Configurable por usuario |
| Auth | Google Identity Services + verificación `id_token` | `spring-security-oauth2-client` no se usa para login; se valida el `aud` con `GOOGLE_CLIENT_ID` |

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
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS}
  music:
    paths: ${MUSIC_PATHS:/srv/Music}          # 50GB de MP3+ en el servidor
    supported-formats: mp3,flac,aac,ogg,m4a,wav
    scan-interval-seconds: ${SCAN_INTERVAL:0} # 0 = solo watcher real-time/manual
  covers:
    cache-path: ${COVERS_CACHE:./data/covers}
    max-size-mb: 500
    retry-days: 7
  lyrics:
    cache-path: ${LYRICS_CACHE:./data/lyrics}
  lastfm:
    api-key: ${LASTFM_API_KEY:}
    api-secret: ${LASTFM_API_SECRET:}
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
- [x] Offline download (IndexedDB/cache)
- [x] Background playback (via PWA/MediaSession API)
- [ ] Transcodificación / quality selector (se traslada a v0.3)
- [ ] Notificaciones nativas (se traslada a v0.3)

### v0.3
- [ ] Transcodificación / quality selector
- [ ] Notificaciones nativas
- [ ] Audiolibros / podcasts
- [ ] Búsqueda avanzada (fulltext PostgreSQL)
- [ ] Chromecast / Bluetooth audio routing
- [ ] Social (followers, actividad)
- [ ] Stats y analytics personales

---

## 12. Notas para Subagentes

- **Siempre usa DTOs.** Nunca expongas `@Entity` directamente en controllers.
- **Los endpoints de stream** (`/tracks/:id/stream`) deben soportar HTTP Range Requests para que el reproductor pueda hacer seek.
- **El scanner no debe bloquear** la app. Usar `@Async` o `ExecutorService`.
- **Carátulas:** si no se encuentra online, usar placeholder generado con color dominante del álbum.
- **Frontend mobile:** la barra de reproducción debe estar fija abajo, con altura mínima 64px.
- **Zustand stores separados:** `playerStore`, `queueStore`, `authStore`, `libraryStore`.
- **Commits:** mensajes en inglés, formato conventional commits: `feat:`, `fix:`, `docs:`, `refactor:`.

---

## 13. Documentación de la API (OpenAPI / springdoc)

- springdoc-openapi está en el classpath. En perfil `dev`, la spec vive en
  `/v3/api-docs` y Swagger UI en `/swagger-ui`. En `prod` está **deshabilitado**
  (`springdoc.api-docs.enabled=false` y `swagger-ui.enabled=false`).
- Snapshot de referencia commiteado: `docs/api/openapi.yaml` (se regenera desde
  `/v3/api-docs.yaml`). Si cambian endpoints o DTOs, regenerar y actualizar el snapshot.
- Metadata y esquema de seguridad Bearer: `config/OpenApiConfig.java`.

---

*Última actualización: 2026-08-12*
