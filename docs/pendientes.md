# Pendientes — NeonVibe

> Lista de tareas que requieren acceso al servidor Debian Trixie o configuración real.

---

## Testing y CI — COMPLETADO (2026-09-23)

- [x] **Backend:** 428 tests (`./mvnw test`); cobertura 84.8% líneas / 66.0% ramas,
  con JaCoCo forzando el umbral (líneas ≥ 82%, ramas ≥ 63%).
- [x] **PostgreSQL real:** `PostgresMigrationTest` (Testcontainers) aplica Flyway
  `V1..V7` y valida el esquema con `ddl-auto: validate`.
- [x] **Frontend:** Vitest + Testing Library (jsdom), 24 tests.
- [x] **CI:** GitHub Actions (`.github/workflows/ci.yml`): backend tests, frontend
  tests/build y empaquetado del JAR.
- [x] **Bugs corregidos durante el testing:** doble lectura del scanner, cierre del
  watcher, 400 en errores de cliente, suscripción cruzada al topic de sync y
  firmado de Last.fm.
- [ ] **Verificación end-to-end en servidor:** escaneo real de `/srv/Music` y
  scrobbling real con claves Last.fm (ver más abajo).

---

## Deploy en producción — EN VIVO (2026-08-12)

- [x] **MVP desplegado** en `https://neonvibe.fepdev.app` (JAR único + systemd + Cloudflare).
- [x] **Login de Google funcional** — verificado end-to-end (build con `VITE_GOOGLE_CLIENT_ID` + `GOOGLE_CLIENT_ID` + `ALLOWED_EMAILS`).
- [x] **Biblioteca visible** — álbumes, artistas y tracks se listan y se reproducen.
- [x] **Fix login 401** — el frontend guardaba el token después de `fetchMe()`; añadido `setToken()` antes.
- [x] **Fix React #310** — hooks (`useMemo`) movidos arriba de los early returns en las páginas de detalle.
- [x] **Fix WebSocket 405** — el SPA catch-all sombreaba `/ws`; el fallback SPA vive en `GlobalExceptionHandler`.
- [x] **Bug del scanner (doble lectura)** — corregido: `MetadataExtractor.extractFile`
  lee cada archivo una sola vez (metadata + artwork embebido) y `FileWatcherService`
  cierra sin excepción. Queda verificar el escaneo real end-to-end en el servidor
  (`POST /api/v1/admin/scan`).

---

## Setup Local (esta PC) — COMPLETO

Desarrollo movido a esta PC (2026-08-06). Infra local lista:

- [x] **PostgreSQL dev en Docker** — `docker compose -f docker-compose.dev.yml up -d`
  - Container `neonvibe-db`, host port **5433** (el PostgreSQL local 18 ocupa 5432).
  - DB/user/pass: `neonvibe`.
- [x] **Backend boot con dev profile**
  - `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` → `GET /actuator/health` = UP.
  - Flyway V1, V2, V3 aplicadas sin errores.
  - Tests del backend en verde (`./mvnw test`).
  - Fix aplicado: import `java.io.InputStream` faltante en `StreamController.java` (rompía la compilación local).
- [x] **Frontend** — `pnpm install` (387 paquetes), `pnpm dev` en `localhost:5173`, `pnpm build` OK (integra a `backend/src/main/resources/static`).
- [ ] **Escaneo real local** — crear MP3s de prueba en `/tmp/test-music` y disparar `POST /api/v1/admin/scan`.

---

## Acceso al Servidor (usuario `felipep` o permisos elevados)

- [x] **Verificar conexión PostgreSQL (Fase 0)** — resuelto: deploy en producción con Flyway V1-V7 aplicadas.
- [x] **Validar migraciones Flyway en PostgreSQL real (Fase 2)** — resuelto: servicio en prod con migraciones aplicadas.
- [ ] **Escanear biblioteca real: `/srv/Music`**
  - Confirmar permisos de lectura para el usuario que ejecutará NeonVibe.
  - Actualizar `neonvibe.music.paths` en `application-prod.yml` a `/srv/Music`.
  - Trigger escaneo manual: `POST /api/v1/admin/scan`.
  - Verificar que 4307+ archivos se detectan, metadatos se extraen, DB se popula.
  - Revisar `ScannerStatus` para archivos fallidos.

- [x] **Crear usuario y base de datos PostgreSQL para producción** — resuelto en deploy (`/opt/neonvibe/neonvibe.env`).

---

## Endurecimiento de auth — RESUELTO (2026-08-10)

- [x] **Verificación del `aud` del id_token de Google**
  - `AuthService` rechazaba solo tokens inválidos, no tokens emitidos para *otras*
    apps de Google (confused deputy: cualquier id_token de otra web servía).
  - Ahora compara `aud` contra `neonvibe.auth.google.client-id` (`GOOGLE_CLIENT_ID`)
    y exige `email_verified`. Sin client id configurado, rechaza el login.
- [x] **Allowlist de cuentas** (`ALLOWED_EMAILS`)
  - Antes cualquier cuenta de Google se auto-registraba y accedía a la biblioteca.
  - Ahora `AccountNotAllowedException` → 403. Lista vacía = sin restricción (dev).
- [x] **`ProdStartupGuard`** exige `GOOGLE_CLIENT_ID` y `ALLOWED_EMAILS` no vacíos
  en perfil `prod` (verificado: el JAR se niega a arrancar sin ellos).
- [x] **`systemctl enable`** — `setup.sh` no lo hacía; el servicio no habría
  arrancado tras un reboot. Añadido.
- [x] **`build.sh` exige `VITE_GOOGLE_CLIENT_ID`** — antes generaba en silencio un
  JAR donde nadie podía iniciar sesión. `ALLOW_NO_GOOGLE=1` para saltarlo.
- [x] **`deploy.sh` comprueba root** antes de tocar systemd.

---

## Configuración y Secrets

- [x] **JWT_SECRET real** — resuelto: generado por `setup.sh`, vive en `/opt/neonvibe/neonvibe.env`.
- [x] **Google OAuth2 credentials** — resuelto: login en producción con GIS + validación `aud`; el `client_secret` vive fuera del repo en `~/.secrets/neonvibe/`.
- [ ] **Last.fm API key (Fase 10)**
  - Crear cuenta/API key en last.fm/api.
  - Guardar en variables de entorno (`LASTFM_API_KEY`, `LASTFM_API_SECRET`).
- [x] **Cloudflare** — resuelto: DNS + proxy en `neonvibe.fepdev.app`.

---

## Optimizaciones y Ajustes

- [x] **H2 scope en producción** — RESUELTO (2026-08-10)
  - `pom.xml`: H2 movido de `runtime` a `test`. Verificado: no aparece en
    `BOOT-INF/lib/` del JAR empaquetado. Los tests siguen pasando.

- [x] **Constraint de PlaylistTrack position** — RESUELTO en Fase 6
  - Se eliminó el unique index `uq_playlist_tracks_position` (V4) para permitir reorder transitorio.
  - El servicio reindexa y ordena por posición en app; `PlaylistService.reorder` valida ids desconocidos/duplicados (400).

- [ ] **Permisos de `/srv/Music`**
  - Propietario actual: `felipep:felipep` (755).
  - Si NeonVibe corre como otro usuario, añadir a grupo `felipep` o ajustar ACLs.
  - Documentar usuario de ejecución final en `docs/DEPLOY.md`.

---

## Fases Futuras

- [x] **Fase 3:** Streaming HTTP Range + WebSocket (completo — verificar WS auth end-to-end en servidor)
- [x] **Fase 4:** Frontend Shell (completo — verificar visualmente en navegador)
- [x] **Fase 5:** Librería (grids álbumes/artistas, tracks, búsqueda) — completo local
- [x] **Fase 6:** Playlists + Favoritos — completo local
- [x] **Fase 7:** Reproductor + sync — completo local (validado WS end-to-end con Node)
- [x] **Fase 8:** Carátulas + letras + visualizador — completo local
- [x] **Fase 9:** PWA + offline — completo local (validación offline manual pendiente)
- [x] **Fase 10:** Radio + scrobbling + compartir — completo local
- [x] **Fase 11:** Deploy — scripts, config prod, login prod y docs listos; ejecutar en servidor
  - Pasos en servidor: `sudo ./deploy/setup.sh` → editar `/opt/neonvibe/neonvibe.env` → `./deploy/build.sh` → `sudo ./deploy/deploy.sh`
  - Ver `docs/DEPLOY.md`

---

## Pendientes Fase 10 (verificación en navegador)

- [ ] **Last.fm real** — configurar `LASTFM_API_KEY` + `LASTFM_API_SECRET`, conectar desde Settings, completar un track y verificar el scrobble en last.fm. El firmado (`api_sig`, exclusión de `format`) ya está corregido; falta la prueba con claves reales.
- [ ] **Radio** — botón Radio en PlayerBar/álbum/artista genera una cola coherente y la reproduce.
- [ ] **Compartir** — en una playlist pública, botón Compartir copia la URL `/p/:id`; abrir en ventana incógnito muestra la playlist sin login (los play requieren sesión).

---

## Pendientes Fase 9 (verificación manual en navegador)

- [ ] **PWA instalable** — build, servir en HTTPS/localhost, Chrome DevTools → Application → Manifest válido; "Añadir a pantalla de inicio" en Android abre standalone.
- [ ] **Offline real**:
  - Descargar un álbum (DownloadButton) → verificar entradas en DevTools → IndexedDB (`neonvibe-offline`).
  - Modo avión: reproducir un track descargado desde el blob; la app shell carga (SW precache).
  - Recargar offline: library/playlists responden stale (SWR `neonvibe-api`).
- [ ] **Polish**: pull-to-refresh en Library/Favorites; swipe entre tabs; sidebar desktop (1440px) vs bottom nav mobile (375px); badge "Sin conexión".
- [ ] **Nota multi-user**: el cache SW de API es por-dispositivo; documentado.

---

## Pendientes Fase 8 (verificación en navegador)

- [ ] **Covers/letras/visualizador en navegador**
  - Covers reales en grids y detalle (álbumes reales obtienen imagen online; los fake usan placeholder neón).
  - Upload manual de cover (álbum/artista) desde la UI — **implementado** (`CoverUploadButton` en AlbumDetail/ArtistDetail).
  - Letras: abrir desde PlayerBar → resalta línea sincronizada con el progreso.
  - Visualizador: botón Waves → barras neón al ritmo; el audio sigue sonando al cerrar.

- [x] **UI de upload manual de carátula** (file input en AlbumDetail/ArtistDetail) — `CoverUploadButton` con validación cliente (MIME + 10 MB), cache-busting `&v=` y refetch de queries.

- [ ] **Genius fallback para letras** (LRCLIB es primaria; Genius requiere scraping).

---

## Pendientes Fase 7 (verificación en navegador)

- [ ] **Reproductor real en navegador**
  - Play/pause/seek contra stream real; next/prev; shuffle/repeat (NONE/ALL/ONE).
  - Cola (QueueSheet) con salto de track; cola persiste tras reload (GET /queue).
  - Sync multi-tab: dos pestañas, tocar play/pause/next en una se refleja en la otra.
  - MediaSession: controles en lockscreen/notificación (Android) y metadatos.
  - `GET /history` refleja reproducciones (completadas y ≥30s).

- [ ] **Refresh de JWT en WS (prod)** — cuando expire, reconectar con token renovado (pendiente flujo OAuth real).

- [ ] **`Cache-Control` del stream en prod** — decidir `no-store` o token de streaming de corta duración (evitar JWT en media cache/logs).

---

## Pendientes Fase 5 (verificación visual en navegador)

- [ ] **Verificar visualmente en navegador real** (`pnpm dev` en 5173 + backend dev)
  - `/library` tabs Canciones/Álbumes/Artistas con datos reales (seed `/tmp/test-music`).
  - Navegación a `/album/:id` (tracklist) y `/artist/:id` (álbumes + canciones).
  - Búsqueda con debounce en `/search` filtra server-side.
  - Scroll infinito (prueba con `size` pequeño en backend o forzando más datos).
  - Skeletons durante carga; estado "sin resultados".
  - Dev bootstrap: sin login manual en dev (token mock automático). Ver `src/api/devBootstrap.ts`.

- [ ] **Flujo OAuth real (prod)** — pendiente en el frontend
  - UI de login (botón "Continuar con Google") + redirect flow OAuth2.
  - Hoy en dev se autologuza con mock; en prod `import.meta.env.DEV` lo bloquea.
  - Documentado para cuando se conecte la cuenta real (antes de Fase 11).


---

## Pendientes Fase 3 (verificación en servidor)

- [ ] **WebSocket auth end-to-end**
  - Conectar con cliente STOMP real (wscat, @stomp/stompjs, o app web) al endpoint `/ws`.
  - Verificar que CONNECT frame con JWT en query param/header funciona.
  - Probar mensajes bidireccionales: `PLAY`, `PAUSE`, `SEEK`, `NEXT`, `PREV`.
  - Verificar que `PLAYER_SYNC` y `QUEUE_UPDATED` llegan al cliente.
  - Documentado para Fase 7/9 (endurecer reconnection).

- [ ] **Streaming sobre MP3 real de `/srv/Music`**
  - Verificar que `GET /api/v1/tracks/{id}/stream` sirve bytes correctamente con Range requests.
  - Probar seek en HTML5 `<audio>` o reproductor web real.
  - Validar MIME types (MP3→audio/mpeg, FLAC→audio/flac, etc.).
  - Confirmar que `FileChannel` manual funciona bien en producción (no tiene problemas de performance vs `ResourceRegion`).

- [ ] **Scanner → WebSocket broadcast**
  - Verificar que `SCANNER_PROGRESS` y `NEW_TRACKS` llegan a clientes conectados durante escaneo real.

---

## Pendientes Fase 4 (verificación visual)

- [ ] **Verificación visual en navegador real**
  - Layout a 375px (mobile) y 1440px (desktop).
  - Toggle dark/light persiste al recargar.
  - Navegación SPA sin recargas.
  - Bottom nav fija, contenido scrolleable.
  - PWA instalable en Chrome DevTools → Application → Manifest.

- [ ] **Icons PWA**
  - Reemplazar SVG placeholders con PNG 192x192 y 512x512 reales con máscara.
  - Documentado para Fase 9 pero puede adelantarse.

---

*Última actualización: 2026-09-23*
