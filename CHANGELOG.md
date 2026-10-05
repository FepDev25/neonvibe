# Changelog

Todos los cambios notables del proyecto se documentan en este archivo.

El formato sigue [Keep a Changelog](https://keepachangelog.com/es/1.1.0/) y el
proyecto respeta [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Subida de música al servidor:** `POST /api/v1/admin/upload` (admin) acepta
  varios ficheros de audio o un **ZIP**; se escriben en un staging fuera de la
  biblioteca, se leen sus tags, se **mueven a `<Artista>/<Álbum>/`**, se ingieren
  y se les aplica la carátula lateral (`cover.jpg/png`). El ZIP se extrae de forma
  segura (anti *zip-slip* y *zip-bomb*, solo audio/imágenes). Además, una
  **carpeta de entrada** vigilada (`<root>/incoming`, configurable) permite dejar
  lotes por SFTP/rsync y se organizan solos. UI de subida (drag & drop, progreso
  y resultado) en su propia página **`/upload`**.
- **Edición de metadata desde la UI:** endpoint y diálogo para editar los tags
  de una canción (`PUT /tracks/{id}/metadata`), de un álbum completo
  (`PUT /albums/{id}/metadata`: nombre, año, género, propagado a todos sus
  ficheros) y de un artista (`PUT /artists/{id}/metadata`: renombrado propagado
  a sus canciones y álbumes). La edición **escribe en los ficheros reales**
  con jaudiotagger (`AudioTagWriter`) y refleja el mismo estado en la BD; es
  solo-admin y devuelve `422` si el fichero no se puede escribir (p. ej.
  filesystem de solo lectura). Botones de edición en cada fila de canción,
  en el detalle de álbum y en el de artista.
- **Búsqueda en las listas:** buscador en `/library` (compartido entre los tabs de
  canciones, álbumes y artistas), `/albums` y `/artists`, con un componente
  reutilizable `SearchInput` y debounce (300 ms) sobre el filtro `q` del backend.
- **Fulltext (`pg_trgm`):** migración **V10** con la extensión `pg_trgm` e índices
  **GIN** (`lower(col) gin_trgm_ops`) sobre títulos, artistas y álbumes, para que la
  búsqueda "contains" use índice en lugar de un seq scan.
- **Indicador "now playing":** en todos los listados de canciones (biblioteca,
  álbumes, playlists, búsqueda, favoritos y cola) la canción actual muestra barras
  ecualizadoras animadas mientras suena y recupera su número al pausar; respeta
  `prefers-reduced-motion`.
- **Vista "Now Playing":** burbujas de luz animadas (orbs neón que flotan y
  escalan) en el fondo, en ambos temas; respetan `prefers-reduced-motion`.
- **Stats y analytics personales:** página `/stats` con resumen (tiempo escuchado,
  reproducciones, artistas/canciones distintas), actividad en el tiempo, top de
  artistas/canciones/álbumes/géneros y reproducciones por hora. Backend con
  agregaciones JPQL y bucketing por la zona horaria del cliente; gráficas CSS sin
  dependencias extra.
- **Quality selector (transcodificación on-demand):** cache-transcode a **AAC**
  con FFmpeg (presets Alta 320 / Normal 192 / Ahorro 128, además de Original sin
  tocar). Solo transcodifica si el origen es lossless o de mayor bitrate que el
  objetivo; si no, sirve el original. Caché configurable con evicción LRU y
  degradación a original si falta FFmpeg. Parámetro `quality` en el stream,
  `GET /api/v1/transcode/status` y selector en la vista "Now Playing".
- **Notificaciones nativas (Web Push):** migración **V11** (`push_subscriptions`),
  endpoints `/api/v1/push/*` (clave pública, suscribir/desuscribir, prueba) y envío
  con VAPID (`VAPID_PUBLIC_KEY`/`VAPID_PRIVATE_KEY`). Service worker con handler de
  `push`/`notificationclick`, toggle real en Ajustes (con botón "Probar") y aviso
  al completar un escaneo. La función queda deshabilitada si no hay claves VAPID.
- **Reproductor — "Now Playing":** al pulsar la canción en la barra se abre una
  vista a pantalla completa (funciona en móvil y escritorio) con carátula grande,
  halo ambiental del propio cover, controles, volumen y acciones: favorito, añadir
  a playlist, letras, radio y visualizador.
- **Favorito y añadir a playlist desde la reproducción**, tanto en la vista
  expandida como (en escritorio) en la propia barra.
- **Descarga de una sola canción:** acción en la vista "Now Playing" y en la barra
  de reproducción (escritorio), con estados descargar / progreso / eliminar,
  además de álbumes y playlists completos.
- **Menú móvil (drawer):** botón de hamburguesa en el header y panel deslizante
  con todas las rutas (antes la barra inferior ocultaba playlists, álbumes,
  artistas y favoritos).

### Changed

- **Barra de reproducción:** la pista ya avanza como una "estela de luz"
  (gradiente cyan→pink) hasta el pulgar, con thumb neón; los controles tienen
  `hover` con fondo y `title` (tooltips) en escritorio.
- **Navegación móvil:** se reemplaza la barra inferior por el drawer; la
  navegación (`navItems`) es ahora una única fuente compartida con el sidebar.

### Fixed

- **Escaneo completo vs. carpeta de entrada:** el escaneo recorría también
  `incoming` y la ingería **en su sitio** (sin mover a `<Artista>/<Álbum>/`),
  pudiendo duplicar pistas; ahora el barrido **omite** `incoming` y, antes de
  empezar, **organiza lo pendiente** que haya en ella. Para añadir música no
  hace falta pulsar «Escanear»: el watcher organiza `incoming` en tiempo real.
- **Subida manual de carátulas:** funcionaba mal de punta a punta. El cliente
  rechazaba el fichero si el navegador reportaba un MIME vacío o inusual (ahora
  valida MIME **o extensión**), el service worker servía la imagen antigua tras
  subir (StaleWhileRevalidate + `ignoreSearch` ignoraba el cache-buster `?v=`;
  ahora las carátulas son **NetworkFirst**), y los errores salían genéricos
  (ahora se muestra el motivo real: 403 admin, 413 tamaño, mensaje del server).
  Además `MaxUploadSizeExceededException` devuelve `413` (antes `500`) y
  `max-request-size` gana margen sobre el límite de fichero de 10 MB.
- **Stats en producción:** las agregaciones usaban `(:param IS NULL OR col >= :param)`;
  en PostgreSQL un parámetro nulo lanzaba `could not determine data type of
  parameter` y `/api/v1/stats/*` devolvía 500 (la página salía vacía). El rango
  «Todo» usa ahora `Instant.EPOCH` como límite inferior; se añade un test de las
  agregaciones contra PostgreSQL real.
- **Página de Stats:** si la API falla ahora muestra un error con botón
  «Reintentar» en lugar del mensaje de "sin datos".
- **PWA móvil — datos obsoletos:** las lecturas de API del service worker pasan a
  `NetworkFirst` (antes `StaleWhileRevalidate`). Al crear una playlist o cambiar
  el tema, la recarga del listado/ajustes ya no servía la respuesta cacheada
  antigua en móvil (en escritorio, sin SW, se veía bien).
- **Tema (móvil):** la hidratación del tema desde los ajustes del servidor ocurre
  una sola vez en el shell, no en cada visita a Ajustes, por lo que salir y volver
  a entrar ya no revierte el tema cambiado desde el header.
- **Tema:** el toggle del header ya no se revierte al estar en Ajustes. El tema
  se hidrata una sola vez desde los ajustes del servidor y las actualizaciones
  son optimistas en el caché, evitando que un `settings.theme` obsoleto deshaga
  el cambio.
- **Orden de resultados:** los listados y búsquedas de tracks, álbumes y artistas
  devuelven un orden determinista (alfabético, insensible a mayúsculas, con `id`
  como desempate) cuando el cliente no pasa `sort`, en lugar de un orden
  indefinido; se respeta un `sort` explícito si se envía.


## [0.2.0] - 2026-10-02

Segunda versión: hardening de seguridad, concurrencia, rendimiento y
accesibilidad, más features de sesión, historial, scanner y descargas.

### Added

- **Sesión:** rotación y **revocación de refresh tokens** (`POST /auth/logout`
  sube `users.token_version`); el frontend renueva el access token al recibir un
  `401` y reintenta la petición (un solo refresh compartido entre 401 concurrentes).
- **Administración:** allowlist `ADMIN_EMAILS` y `AdminGuard`; el escaneo manual y
  la subida de carátulas requieren rol admin.
- **UI de scanner** en Ajustes (disparar escaneo, contadores y eventos
  `SCANNER_PROGRESS`/`NEW_TRACKS` por WebSocket).
- **Página de historial** (`/history`) y **gestión de descargas offline**
  (`/downloads`).
- `GET /history` devuelve entradas enriquecidas con título/artista/álbum (fetch
  join, sin N+1).
- **Testing:** backend 486 tests y frontend 66 tests.

### Security

- springdoc/OpenAPI ya no se expone en producción: las claves estaban anidadas
  bajo `spring:` y se ignoraban (la spec y Swagger UI quedaban públicos).
- Subida de carátulas: **solo admin**, tipo real detectado por **magic bytes**
  (PNG/JPEG/WebP), **SVG rechazado** y cabecera `X-Content-Type-Options: nosniff`.
- `JWT_SECRET` sin valor por defecto en la configuración base (fail-fast).
- `filePath` y `coverArtPath` excluidos del JSON (`@JsonIgnore`) para no exponer
  rutas absolutas del servidor.
- El principal autenticado transporta `email`/`name` desde los claims del JWT.

### Changed

- `PlayQueue` con **optimistic locking** (`version`) y reintentos ante conflictos
  o carreras de creación.
- El escaneo completo es **exclusivo** (no se solapan watcher, scheduler y admin).
- **N+1** eliminados: conteos por álbum/artista con una query agrupada y
  `@BatchSize` en playlists; la radio usa consultas acotadas en vez de cargar toda
  la biblioteca.
- Se eliminó la colección inversa `Album.tracks` (código muerto y semántica de
  borrado engañosa).
- `AuthService` y `LyricsService` ya no mantienen una transacción durante llamadas
  HTTP externas; `AuthService` usa el `externalRestClient` con timeouts.
- `NEXT`/`PREV` aceptan el `track_id` elegido por el cliente (shuffle coherente
  multi-dispositivo).
- `GET /queue` responde `204` cuando no hay cola.

### Fixed

- Scrobbling de tracks completados (el dedupe se evaluaba después de insertar y se
  bloqueaba a sí mismo).
- Historial vía WebSocket: registra el track **saliente**, no el nuevo.
- Logout: cierra el WebSocket y limpia cola/favoritos/cachés del usuario anterior.
- El tema claro persiste al recargar; el toggle del header también lo guarda en BD.
- El seek remoto no fuerza `is_playing=true`; el historial usa la duración real.
- Frontend: orden de sync (`QUEUE_UPDATED` antes de `PLAY`), shuffle cliente-autoritativo,
  reintento de carátula al cambiar de `src`, `SeekBar` al soltar fuera, MIME real en
  descargas, `openDb` reintentable, modales con foco/Escape, tabs `tablist`, label de
  búsqueda, tarjetas sin `<button>` dentro de `<a>`, y ruptura del import circular
  `playerStore ⇄ audioGraph`.
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

### Testing

- **Testing backend:** suite de 123 a 486 tests (JUnit 5 + Mockito +
  Spring Boot Test) cubriendo seguridad, scanner, servicios de biblioteca,
  controladores, clientes externos, repositorios, mappers, excepciones y
  configuración.
- **Testing frontend:** Vitest + Testing Library (jsdom) con tests de utilidades,
  hooks, stores y componentes.
- **PostgreSQL real:** smoke test con Testcontainers que aplica las migraciones
  Flyway V1–V9 y valida el esquema con `ddl-auto: validate`; se salta
  automáticamente cuando no hay Docker.
- **Cobertura:** JaCoCo con reporte HTML/CSV y umbral en el build (líneas ≥ 82%,
  ramas ≥ 63%).
- **CI:** GitHub Actions (`.github/workflows/ci.yml`) con tests de backend, tests
  y build de frontend y empaquetado del JAR; publica los artefactos de cobertura
  y del JAR.
- **Fixtures de audio** (MP3 con tags ID3 y artwork embebido) para probar la
  extracción real de metadatos de jaudiotagger.

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
