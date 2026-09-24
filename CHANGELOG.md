# Changelog

Todos los cambios notables del proyecto se documentan en este archivo.

El formato sigue [Keep a Changelog](https://keepachangelog.com/es/1.1.0/) y el
proyecto respeta [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Testing backend:** suite ampliada de 123 a 428 tests (JUnit 5 + Mockito +
  Spring Boot Test) cubriendo seguridad, scanner, servicios de biblioteca,
  controladores, clientes externos, repositorios, mappers, excepciones y
  configuración.
- **Testing frontend:** Vitest + Testing Library (jsdom) con tests de utilidades,
  hooks, stores y componentes.
- **PostgreSQL real:** smoke test con Testcontainers que aplica las migraciones
  Flyway V1–V7 y valida el esquema con `ddl-auto: validate`; se salta
  automáticamente cuando no hay Docker.
- **Cobertura:** JaCoCo con reporte HTML/CSV y umbral en el build (líneas ≥ 82%,
  ramas ≥ 63%).
- **CI:** GitHub Actions (`.github/workflows/ci.yml`) con tests de backend, tests
  y build de frontend y empaquetado del JAR; publica los artefactos de cobertura
  y del JAR.
- **Fixtures de audio** (MP3 con tags ID3 y artwork embebido) para probar la
  extracción real de metadatos de jaudiotagger.

### Fixed

- Scanner: leer cada archivo una sola vez (metadata + artwork embebido) en lugar
  de dos, que ralentizaba y hacía fallar escaneos grandes.
- Scanner: el hilo del watcher ya no muere con `ClosedWatchServiceException` al
  apagar el servicio.
- API: parámetros de query o cuerpos malformados devuelven `400` (antes `500`).
- WebSocket: se rechaza la suscripción a `/topic/sync/{otroUsuario}`, que permitía
  escuchar el estado del reproductor y la cola de otro usuario.
- Last.fm: `track.scrobble` se firma correctamente y el `api_sig` excluye
  `format`/`callback`, según la especificación oficial (auth y scrobbling
  estaban rotos).

## [0.1.0] - 2026-08-12

Primera versión desplegada en producción (`https://neonvibe.fepdev.app`).

### Added

- **Autenticación:** login con Google Identity Services + JWT interno
  (access + refresh), verificación del `aud` del `id_token` y allowlist de
  cuentas (`ALLOWED_EMAILS`).
- **Scanner en tiempo real:** detección de cambios en el filesystem
  (`WatchService`) + extracción de metadatos y trigger manual de escaneo.
- **Biblioteca:** tracks, álbumes y artistas con búsqueda y listados paginados.
- **Streaming:** HTTP Range requests con seek, MIME types por formato.
- **Reproductor:** cola persistente, historial, shuffle, repeat, crossfade,
  MediaSession API (background playback) y visualizador Web Audio + Canvas.
- **Playlists:** CRUD, reordenamiento, compartir con vista pública.
- **Favoritos:** tracks, álbumes y artistas.
- **Carátulas:** descarga automática (MusicBrainz, iTunes, Last.fm) con caché
  local y upload manual.
- **Letras:** LRCLIB con caché local.
- **Radio por similitud:** generación de colas basadas en el track semilla.
- **Scrobbling:** integración con Last.fm (conectar/desconectar cuenta).
- **Sync multi-dispositivo:** WebSocket STOMP sobre SockJS
  (`PLAYER_SYNC`, `QUEUE_UPDATED`, eventos de scanner).
- **PWA:** service worker con precache, descarga offline en IndexedDB y
  soporte instalable.
- **UI:** tema neón dark/light, mobile-first, modo claro/oscuro.

### Changed

- El frontend se sirve embebido en el JAR del backend (un único artefacto).
- `deploy/build.sh` exige `VITE_GOOGLE_CLIENT_ID` y separa `NODE_ENV` para
  `pnpm install` vs `vite build`.
- El scanner dejó de ejecutar un escaneo completo en el arranque.

### Fixed

- Login: el token de acceso se guardaba después de `fetchMe()`, provocando un
  `401` en `GET /auth/me`; el token ahora se almacena antes.
- Páginas de detalle (álbum, artista, playlist): `useMemo` después de un early
  return causaba el error de React #310; los hooks se movieron arriba de los
  returns condicionales.
- WebSocket: el fallback SPA (catch-all `@GetMapping`) sombreaba el endpoint
  `/ws` (405 en `GET /ws/info`); el fallback SPA vive ahora en el handler global
  de excepciones y excluye `/api`, `/ws` y `/actuator`.
- Seguridad: verificación del `aud` del `id_token` (evita el confused deputy) y
  allowlist de correos obligatoria en producción.
- `systemctl enable` en `setup.sh` (el servicio no arrancaba tras un reboot).
- H2 fuera del JAR de producción (scope `test`).

### Security

- Secretos fuera del repositorio: `JWT_SECRET`, credenciales de Google y
  variables de entorno en `/opt/neonvibe/neonvibe.env` (modo `0600`).
- `ProdStartupGuard`: la app no arranca en `prod` sin `JWT_SECRET`,
  `GOOGLE_CLIENT_ID` y `ALLOWED_EMAILS`.
- CORS y orígenes WebSocket restringidos a `CORS_ALLOWED_ORIGINS` / `WS_ORIGINS`.
