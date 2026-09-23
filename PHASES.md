# Fases de Desarrollo — NeonVibe

> Roadmap de fases. Cada fase es un sprint cerrado con entregables validables. No avanzamos a la siguiente hasta que la actual esté estable.

**Estado (2026-08-12):** las 11 fases (0-11) están implementadas y el MVP está
desplegado en producción (`https://neonvibe.fepdev.app`). Este documento se
mantiene como referencia del trabajo realizado; los pendientes activos viven en
`docs/pendientes.md`.

---

## Principios

1. **Una fase, un subagente (o grupo coordinado).** No paralelizamos trabajo que dependa del mismo código.
2. **Backend primero, frontend después.** Las fases 1-3 son backend. Las fases 4-8 son frontend integrado.
3. **Validar antes de merge.** Cada fase termina en una PR/branch funcional que compilamos y testeamos.
4. **MVP primero, lujo después.** Si algo se puede simplificar, se simplifica.

---

## Fase 0: Bootstrap y Arquitectura Base
**Objetivo:** Tener el proyecto compilando, corriendo localmente, y con la DB conectada.
**Subagentes:** 1 (backend + infra)
**Dependencias:** Ninguna

### Entregables
- [x] Proyecto Maven `backend/` con Spring Boot 3.4+, Java 21, packaging JAR
- [x] `application.yml` + `application-dev.yml` con profiles separados
- [x] Docker Compose con PostgreSQL 16 para dev (`docker-compose.dev.yml`)
- [x] Estructura de paquetes: `config`, `controller`, `service`, `repository`, `domain`, `dto`, `security`, `infra`
- [x] Configuración base: CORS, Jackson, validación, exception handler global (`@ControllerAdvice`)
- [x] Flyway activo con script `V1__init_schema.sql` vacío (placeholder)
- [x] Health endpoint `GET /actuator/health` respondiendo
- [x] Script `mvnw spring-boot:run` funcional

### Criterios de aceptación
- `./mvnw spring-boot:run` levanta sin errores.
- `docker compose -f docker-compose.dev.yml up` levanta PostgreSQL accesible.
- `GET http://localhost:8080/actuator/health` devuelve `UP`.

---

## Fase 1: Autenticación y Usuarios
**Objetivo:** Login con Google OAuth2 + JWT interno. Sistema de usuarios funcional.
**Subagentes:** 1
**Dependencias:** Fase 0

### Entregables
- [x] Entidad `User` (id, email, name, avatar_url, google_id, created_at)
- [x] Integración OAuth2 Google (`spring-security-oauth2-client`)
- [x] JWT propio para sesiones internas (access + refresh)
- [x] Endpoints: `POST /auth/google`, `POST /auth/refresh`, `GET /auth/me`
- [x] Security filter: rutas públicas vs protegidas
- [x] DTOs `UserResponse`, `AuthRequest`, `TokenResponse`

### Criterios de aceptación
- Flujo de login con Google redirecciona y devuelve JWT.
- `GET /auth/me` con JWT válido devuelve datos del usuario.
- `GET /auth/me` sin token devuelve 401.

---

## Fase 2: Dominio Musical y Scanner
**Objetivo:** El sistema entiende tu biblioteca. Scanner detecta archivos y extrae metadatos.
**Subagentes:** 2 (uno para entidades/API, otro para scanner)
**Dependencias:** Fase 0-1

### Entregables
- [x] Entidades JPA: `Track`, `Album`, `Artist`, `Playlist`, `PlaylistTrack`, `PlayQueue`, `PlayHistory`, `Favorite`
- [x] Repositories Spring Data JPA + métodos de búsqueda básicos
- [x] Flyway `V2__music_schema.sql` con todas las tablas, índices, constraints
- [x] Scanner con `WatchService` (o polling si WatchService no detecta todos los eventos) sobre rutas configurables
- [x] Extractor de metadatos: lee ID3 (MP3) y Vorbis comments (FLAC/OGG) — usar librería como `jaudiotagger` o `musicmetadata`
- [x] Mapeo automático: archivo → Track → Album/Artist (crear si no existen, update si cambian tags)
- [x] Endpoints REST: `GET /tracks`, `GET /tracks/:id`, `GET /albums`, `GET /albums/:id`, `GET /artists`, `GET /artists/:id`
- [x] Endpoint admin: `POST /admin/scan` (trigger manual) + `GET /admin/scan/status`
- [x] DTOs para todas las respuestas (no exponer entidades)

### Criterios de aceptación
- Escanear `/srv/Music` (o path de test) crea registros en DB.
- Tags básicos (title, artist, album, year, genre, track_number) se extraen correctamente de MP3.
- `GET /tracks` devuelve paginación (page/size) con datos reales.
- Si borro un archivo, el scanner lo marca como unavailable (soft delete o flag).

---

## Fase 3: Streaming y Reproductor Backend
**Objetivo:** Servir audio por HTTP con seek funcional. WebSocket base para sync.
**Subagentes:** 1
**Dependencias:** Fase 2

### Entregables
- [x] `GET /tracks/:id/stream` con soporte **HTTP Range Requests** (header `Range: bytes=...`)
- [x] Determinación de MIME type según extensión/formato
- [x] WebSocket config: STOMP sobre WebSocket con tema `/topic/sync/{userId}`
- [x] Entidad `PlayQueue` actualizada + API: `GET /queue`, `PUT /queue`
- [x] Entidad `PlayHistory` + API: `POST /history` (registrar reproducción)
- [x] Player state DTO (track_id, position_ms, is_playing)

### Criterios de aceptación
- Reproductor HTML5 (`<audio>`) puede hacer seek en cualquier punto de la canción.
- `206 Partial Content` en respuestas de stream.
- WebSocket conecta desde cliente de prueba (ej: wscat) y recibe/envía mensajes.

---

## Fase 4: Frontend Shell y Tema
**Objetivo:** La app existe en el browser. Tema neón, routing, layout mobile-first.
**Subagentes:** 1 (frontend setup)
**Dependencias:** Fase 0

### Entregables
- [x] `frontend/` con Vite + React 18 + TypeScript (strict)
- [x] pnpm configurado, `.npmrc` con `strict-peer-dependencies=false` si aplica
- [x] Tailwind CSS configurado con paleta neón (CSS variables en `:root`)
- [x] Sistema de temas: `dark` (default) + `light` + toggle global
- [x] React Router v6 con rutas base: `/`, `/library`, `/search`, `/settings`, `/player`
- [x] Layout mobile-first: bottom nav bar (64px), top header, content scrollable
- [x] Zustand stores: `authStore` (placeholder), `themeStore`
- [x] PWA: `manifest.json`, `vite-plugin-pwa` con service worker mínimo
- [x] `README.md` frontend con instrucciones de dev

### Criterios de aceptación
- `pnpm dev` levanta en `localhost:5173`.
- Se ve bien en viewport 375px (mobile) y 1440px (desktop).
- Toggle dark/light funciona y persiste en `localStorage`.
- Navegación entre rutas sin recarga.
- PWA es instalable (aparece "Add to Home Screen" en Chrome DevTools).

---

## Fase 5: Librería y Navegación
**Objetivo:** Ver la música. Grids de álbumes, listas de tracks, artistas.
**Subagentes:** 1
**Dependencias:** Fase 2, Fase 4

### Entregables
- [x] TanStack Query configurado (React Query) con cliente HTTP (`axios` o fetch wrapper)
- [x] Vista `/library`: tabs de Álbumes / Artistas / Canciones
- [x] Componente `AlbumCard`: carátula placeholder (color sólido o gradient), título, artista
- [x] Componente `TrackRow`: número, título, duración, botón play
- [x] Vista `/album/:id`: detalle de álbum con tracklist
- [x] Vista `/artist/:id`: discografía del artista
- [x] Scroll infinito o paginación (page + size)
- [x] Búsqueda básica: input en header que filtra tracks/albumes/artistas (cliente + server)

### Criterios de aceptación
- Cargar `/library` muestra álbumes reales desde `GET /albums`.
- Click en álbum navega a detalle y muestra tracks.
- Búsqueda filtra resultados sin recargar página.
- Placeholders de carga (skeletons) mientras TanStack Query fetcha.

---

## Fase 6: Playlists y Favoritos
**Objetivo:** CRUD completo de playlists y sistema de favoritos.
**Subagentes:** 1
**Dependencias:** Fase 2, Fase 5

### Entregables
- [x] Vista `/playlists`: listado de playlists del usuario
- [x] Modal/flow crear playlist (nombre, descripción, público/privado)
- [x] Vista `/playlist/:id`: tracks con drag-and-drop reorder (o botones up/down si DnD es complejo)
- [x] Botón "Add to playlist" desde track row o álbum
- [x] Botón "Remove" en playlist track
- [x] APIs: `POST /playlists`, `PUT /playlists/:id`, `DELETE /playlists/:id`, `POST /playlists/:id/tracks`, etc.
- [x] Botón "Favorito" (corazón) en tracks, álbumes, artistas
- [x] Vista `/favorites` con tabs por tipo (tracks, álbumes, artistas)
- [x] Endpoints: `GET /favorites`, `POST /favorites`, `DELETE /favorites/:id`

### Criterios de aceptación
- Crear playlist, añadir tracks, reordenar, eliminar funciona end-to-end.
- Marcar favorito persiste en DB y se refleja en UI inmediatamente.
- Playlist pública vs privada: solo el owner puede editar.

---

## Fase 7: Reproductor Frontend y Sync
**Objetivo:** El reproductor funciona. Stream, controls, queue, history.
**Subagentes:** 1
**Dependencias:** Fase 3, Fase 5

### Entregables
- [x] Barra de reproductor fija (bottom): track actual, play/pause, next/prev, seek bar, volume
- [x] Integración con `HTMLAudioElement` (o Howler.js) apuntando a `/tracks/:id/stream`
- [x] Seek funcional (click en barra de progreso)
- [x] Queue visual: panel deslizable (bottom sheet en mobile) mostrando cola actual
- [x] Botones shuffle, repeat (none/all/one)
- [x] WebSocket client: conecta, envía acciones (`PLAY`, `PAUSE`, `SEEK`, `NEXT`, `PREV`), recibe `PLAYER_SYNC`
- [x] `MediaSession API`: controles en notificación/lockscreen (title, artist, carátula, controles)
- [x] Registro de history: envía `POST /history` al terminar/completar track

### Criterios de aceptación
- Play/pause/seek funciona en mobile (Chrome Android) y desktop.
- Cambiar de canción manualmente o por next/prev actualiza el stream.
- Queue se sincroniza entre pestañas del mismo navegador (via WS).
- MediaSession muestra controles nativos en Android.
- Historial de reproducción aparece en `GET /history`.

---

## Fase 8: Carátulas, Letras y Visualizador
**Objetivo:** Enriquecimiento visual y funcional. La app se ve profesional.
**Subagentes:** 1 (backend integración) + 1 (frontend visual)
**Dependencias:** Fase 2, Fase 5, Fase 7

### Entregables
- [x] Servicio de descarga de carátulas: MusicBrainz (Cover Art Archive), Last.fm, iTunes API, fallback cascade
- [x] Cache de imágenes en filesystem (`./data/covers/` o ruta configurable)
- [x] Endpoint `GET /tracks/:id/cover` y `GET /albums/:id/cover` (serve file o redirect)
- [x] Frontend: mostrar carátulas reales en AlbumCard, ArtistCard, PlayerBar
- [x] Servicio de letras: LRCLIB API (primaria), Genius (fallback)
- [x] Endpoint `GET /tracks/:id/lyrics` — retorna texto plano o sincronizado (`.lrc`)
- [x] Vista "Letras" en el reproductor (panel deslizable o overlay)
- [x] Visualizador de audio: Web Audio API (`AnalyserNode`) + Canvas 2D con barras/ondas neón
- [x] Upload manual de carátula (drag & drop o file input) para álbum/artist

### Criterios de aceptación
- Álbumes sin carátula incrustada obtienen imagen online automáticamente.
- Imagen se guarda en cache y no se re-descarga en cada scan.
- Letras se muestran para al menos 70% de tracks populares (LRCLIB coverage).
- Visualizador responde al audio en tiempo real (30fps+).
- Upload manual persiste y reemplaza carátula automática.

---

## Fase 9: PWA, Offline y Polish
**Objetivo:** La app se siente como una app nativa. Funciona sin conexión parcialmente.
**Subagentes:** 1
**Dependencias:** Fase 4-8

### Entregables
- [x] Service Worker con estrategia de cache para assets estáticos (CacheFirst)
- [x] Cache de API: playlists, favoritos, library reciente (StaleWhileRevalidate)
- [x] Descarga offline: botón "Download" en álbum/playlist que guarda tracks en IndexedDB (o CacheStorage)
- [x] Reproducción offline: detecta sin conexión y usa tracks cacheados
- [x] Responsive final: ajustes en desktop (sidebar + main content vs bottom nav)
- [x] Gestos táctiles: swipe entre tabs, pull-to-refresh en library
- [x] Pantalla de inicio/splash screen adaptada al tema
- [x] Iconos en todas las resoluciones PWA

### Criterios de aceptación
- Instalar PWA en Android abre en modo standalone (sin barra de navegador).
- Activar "modo avión" permite reproducir tracks descargados.
- Pull-to-refresh recarga library sin F5.
- Desktop muestra layout de 2-3 columnas, mobile mantiene single column.

---

## Fase 10: Radio, Scrobbling y Compartir
**Objetivo:** Features avanzadas. La app es completa.
**Subagentes:** 1
**Dependencias:** Fase 2-9

### Entregables
- [x] Radio por similitud: endpoint `GET /radio/seed?track_id=` que genera cola de 20+ tracks similares (mismo género/artista/año)
- [x] Botón "Radio basada en esto" desde track/álbum/artista
- [x] Integración Last.fm: auth OAuth + scrobbling automático al completar 50%+ de track
- [x] Configuración de Last.fm en Settings (connect/disconnect)
- [x] Compartir playlist: URL pública `/playlist/:id` view-only para no-logueados
- [x] Quality selector UI (si backend soporta transcodificación; si no, placeholder)
- [x] Settings page completa: cuenta, tema, notificaciones, sources de carátulas, cache

### Criterios de aceptación
- Radio genera cola coherente (no aleatoria pura, relacionada al seed).
- Reproducir track scrobblea en Last.fm (verificable en perfil Last.fm).
- Playlist pública se ve sin necesidad de login.
- Settings persisten en DB (no solo localStorage).

---

## Fase 11: Deploy y Producción
**Objetivo:** NeonVibe vive en tu servidor Debian Trixie.
**Subagentes:** 1 (DevOps local)
**Dependencias:** Todas las anteriores

### Entregables
- [x] Script de build integrado: Maven build + frontend build copiado a `backend/src/main/resources/static`
- [x] JAR ejecutable con frontend embebido (single artifact)
- [x] Archivo de configuración `application-prod.yml` con paths reales
- [x] systemd service: `/etc/systemd/system/neonvibe.service`
- [x] Script `deploy.sh` (git pull → build → restart service)
- [x] Configuración de Cloudflare (DNS + proxy) documentada
- [x] Variables de entorno para secrets (JWT secret, Google OAuth creds, Last.fm API key)
- [x] Guía `docs/DEPLOY.md`

### Criterios de aceptación
- `systemctl start neonvibe` levanta la app en puerto configurado.
- Acceso desde WAN via dominio + HTTPS (Cloudflare).
- Restart del service no pierde datos ni sesiones activas (graceful shutdown).
- `deploy.sh` funciona sin intervención manual más allá de los secrets.

---

## Flujo de Trabajo por Fase

```
┌─────────┐     ┌─────────┐     ┌─────────┐
│  Felipe │────►│  Flash  │────►│ Subagent│
│         │     │ (yo)    │     │ (code)  │
└─────────┘     └─────────┘     └─────────┘
     ▲                               │
     │         ┌─────────┐          │
     └─────────│  Repo   │◄─────────┘
               │  GitHub │
               └─────────┘
```

1. **Yo armo la fase:** specs detallados, prompts, dependencias claras.
2. **Lanzo subagente(s):** con contexto de `AGENTS.md` + especificaciones de la fase.
3. **Subagente trabaja:** en branch o directo en main (para docs/estructura inicial).
4. **Yo reviso:** compilo, verifico criterios de aceptación.
5. **Felipe valida:** si quieres revisar algo antes de seguir.
6. **Commit + push.**
7. **Siguiente fase.**

---

## Notas

- **Fases 0-3** son backend puro. No tocamos frontend.
- **Fases 4-5** son frontend puro contra APIs ya existentes (mock si aún no existen, pero idealmente con backend real).
- **Fase 6-7** son integración end-to-end.
- **Fases 8-10** son polish y features avanzadas.
- **Fase 11** es deploy.
- Si en cualquier momento una fase crece demasiado, la partimos en sub-fases (A, B, C).

---

*Última actualización: 2026-08-12*
