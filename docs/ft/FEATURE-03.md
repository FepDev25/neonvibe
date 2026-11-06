# FEATURE — NeonVibe: álbum «Singles» + importación desde YouTube (yt-dlp)

**Fase:** 3 — Canciones sueltas y captura rápida de música
**Estado:** Cerrada — desplegada y validada en producción (2026-11-06)
**Origen:** petición del propietario (2026-11-06)
**Alcance:** backend Java/Spring + frontend React + migración Flyway + despliegue
**No es:** un gestor de descargas genérico, un reproductor de YouTube ni una
integración con playlists/canales de YouTube (v1 = un vídeo por importación).

---

## 1. Objetivo

1. **Agrupar todas las canciones sueltas en un álbum real «Singles»**, visible en
   la UI como cualquier otro álbum, sin perder la autoría de cada pista.
2. **Añadir importación desde YouTube en `/upload`**: pegar un enlace, descargar
   el audio con `yt-dlp`, etiquetarlo y dejarlo en la biblioteca dentro de
   «Singles», todo desde la interfaz y solo para administradores.

---

## 2. Diagnóstico del código (estado actual)

### 2.1 Cómo se crean artistas y álbumes

| Paso | Archivo | Comportamiento |
|---|---|---|
| Extracción de tags | `scanner/JAudioTaggerMetadataExtractor.java` | Lee `TITLE`, `ARTIST`, `ALBUM`, `ALBUM_ARTIST`, `YEAR`, `GENRE`… Si falta el título, lo deduce del nombre de archivo. |
| Alta en BD | `scanner/LibrarySyncService.upsert` | `resolveArtist(meta.artist())` y `resolveAlbum(meta.album(), meta.artist(), year, genre)`. |
| Resolución de álbum | `LibrarySyncService.resolveAlbum` | **Si `album` es `null`/vacío devuelve `null`**: la pista queda con `album_id = NULL` y `album = NULL`. |
| Organización en disco | `scanner/IngestService.destinationFor` | Mueve a `<root>/<AlbumArtist o Artist>/<Album o "Unknown Album">/`. |

Consecuencia directa: **una canción sin tag de álbum no tiene entidad `Album`**.
Aparece en *Canciones*, *Búsqueda*, *Historial* y *Favoritos*, pero **no en
Álbumes** (la pestaña lista filas de la tabla `albums`). Es decir: los singles
actuales probablemente «existen» pero son invisibles como colección.

### 2.2 Qué ocurre con un archivo etiquetado como «Single»/«Singles»

Si el tag `ALBUM` vale `Single` o `Singles` (con cualquier artista), hoy se crea
un álbum normal con ese nombre y ese artista: se ve como una card más. Un mismo
artista puede acabar con dos cards («Singles» y «Single») y artistas distintos
con «Singles» separados. No hay ninguna noción de «canción suelta».

### 2.3 Endpoints afectados (sin cambios de contrato obligatorios)

`GET/PUT /albums*` ya sirven para listar, ver pistas, subir portada, marcar
favorito y editar metadata de un álbum real. Si «Singles» es un álbum real,
**no hace falta inventar IDs virtuales ni endpoints nuevos**: hereda todo
(portada, favoritos, descarga, radio, edición admin).

### 2.4 Riesgos detectados en el código actual

- `IngestService.destinationFor` y `LibrarySyncService.resolveAlbum` calculan el
  álbum por separado: si solo se toca uno, el archivo puede acabar en
  `Unknown Album/` mientras la BD dice «Singles». Hay que centralizar la regla.
- La restricción única `(name, artist)` **no protege contra duplicados cuando
  `artist` es `NULL`** (en PostgreSQL los `NULL` son distintos). Por eso el
  álbum global debe llevar un artista no nulo (ver §4.1).
- `AlbumDetailPage` oculta el artista de cada fila (`showArtist={false}`); en un
  álbum con varios artistas eso perdería información.
- `MetadataEditService.updateAlbum` permite renombrar cualquier álbum; renombrar
  «Singles» rompería la reserva del nombre.
- `deploy/setup.sh` instala `ffmpeg` **solo si falta Java** (bloque `if ! command
  -v java`). En un servidor ya preparado, añadir yt-dlp exige un paso aparte.

---

## 3. Diagnóstico de producción (ejecutado 2026-11-06)

Resultado obtenido: **4577 pistas** (4347 disponibles), **44 sin `album_id` y con
tag de álbum vacío**, **0 álbumes** llamados «Single(s)». `yt-dlp 2026.03.17`
estaba instalado solo para `felipep` (`~/.local/bin`), **sin Deno** y sin acceso
para el usuario `neonvibe`; `ffmpeg 7.1.5` disponible.

Los comandos siguientes quedan como referencia para repetir el diagnóstico.
**Solo lectura**: no modifican nada.

```bash
# --- 1) Binarios y runtime JS (yt-dlp >= 2025.11.12 lo exige para YouTube) ---
command -v yt-dlp || echo "yt-dlp NO instalado"
yt-dlp --version 2>/dev/null || true
command -v deno || echo "deno NO instalado"
deno --version 2>/dev/null | head -1 || true
ffmpeg -version | head -1

# --- 2) ¿Puede el usuario del servicio ejecutarlos con el PATH de systemd? ---
sudo -u neonvibe env -i PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
  sh -c 'yt-dlp --version; deno --version | head -1' 2>&1 || true

# --- 3) Estado real de los singles en la BD ---
sudo -u postgres psql -d neonvibe -P pager=off <<'SQL'
SELECT count(*)                                                AS total,
       count(*) FILTER (WHERE is_available)                    AS disponibles,
       count(*) FILTER (WHERE album_id IS NULL)                AS sin_album_id,
       count(*) FILTER (WHERE album IS NULL OR btrim(album)='') AS album_vacio,
       count(*) FILTER (WHERE album_id IS NULL
                          AND (album IS NULL OR btrim(album)='')) AS singles_probables
FROM tracks;

SELECT id, title, artist, album, album_artist, is_available, file_path
FROM tracks
WHERE album_id IS NULL OR album IS NULL OR btrim(album)=''
ORDER BY created_at DESC
LIMIT 25;

SELECT a.id, a.name, a.artist,
       (SELECT count(*) FROM tracks t WHERE t.album_id = a.id) AS pistas
FROM albums a
WHERE lower(btrim(a.name)) LIKE 'single%'
ORDER BY a.name, a.artist;

SELECT name, artist, count(*) AS duplicados
FROM albums
WHERE lower(btrim(name)) IN ('single','singles')
GROUP BY 1, 2
HAVING count(*) > 1;
SQL

# --- 4) Estructura en disco: ¿hay sueltos en la raíz o todo en carpetas? ---
sudo find /srv/Music -maxdepth 2 -type f \
  \( -iname '*.mp3' -o -iname '*.flac' -o -iname '*.m4a' -o -iname '*.aac' \
     -o -iname '*.ogg' -o -iname '*.wav' \) -printf '%h\n' \
  | sort | uniq -c | sort -rn | head -20

# --- 5) Configuración relevante (sin secretos) ---
sudo grep -E '^(MUSIC_|FFMPEG_|TRANSCODE_|YTDLP_)' /opt/neonvibe/neonvibe.env || true
```

**Cómo interpretar la salida**

- `singles_probables > 0` → hay pistas que la migración debe mover a «Singles».
- `sin_album_id > 0` con `album` no vacío → datos antiguos/inconsistentes: la
  migración debe reconstruir su álbum por `(album, artist)` en vez de meterlos
  en «Singles».
- Si ya existe un álbum `Singles`, la migración lo reutiliza (no crea otro).
- Si `yt-dlp` o `deno` no aparecen, la importación de YouTube debe deshabilitarse
  con un aviso claro en la UI hasta instalarlos.

---

## 4. Diseño — álbum «Singles»

### 4.1 Decisión de modelado

**Decisión final: un único álbum real global sin artista**, con:

- `albums.name = "Singles"`
- `albums.artist = NULL` (el propietario no quiere un artista ficticio)
- `tracks.album = "Singles"` y `tracks.album_id = <id de ese álbum>`
- `tracks.artist` se conserva intacto (cada canción mantiene su autoría)

Como la unique `(name, artist)` considera los `NULL` distintos, la migración
crea un **índice único parcial**:

```sql
CREATE UNIQUE INDEX uq_albums_singles
    ON albums (name) WHERE name = 'Singles' AND artist IS NULL;
```

La UI muestra «Varios artistas» solo como **etiqueta de presentación** en la
card; el dato almacenado sigue siendo `artist = NULL`.

Alternativa descartada: un álbum «Singles» **por artista**. Multiplica las cards
y no responde a «todos los singles en una especie de álbum».

Ventajas del álbum real: portada, favoritos, descarga, radio, búsqueda por
álbum, edición de metadata y contadores funcionan sin tocar contratos.

### 4.2 Reglas de normalización (fuente única)

Crear una utilidad, p. ej. `scanner/LibraryCatalog`:

```java
public static final String SINGLES_ALBUM = "Singles";

/** Blank, "single" o "singles" (case-insensitive) -> "Singles". */
static String normalizeAlbumName(String album) { ... }

/** Cierto para el nombre reservado. */
static boolean isSingles(String albumName) { ... }
```

Se aplica en **los tres puntos** que hoy calculan álbum:

1. `LibrarySyncService.resolveAlbum` → nombre normalizado y `artist = NULL`
   cuando es Singles.
2. `LibrarySyncService.upsert` → `track.album` guarda el nombre normalizado.
3. `IngestService.destinationFor` → carpeta `<Artist>/Singles/` (se mantiene el
   artista real en la ruta física; el álbum de BD es el global).

Regla de carpetas resultante:

```
/srv/Music/<Artista real>/Singles/<archivo>.mp3     ← canciones sueltas
/srv/Music/<Artista real>/<Álbum real>/<archivo>.mp3
```

### 4.3 Migración de datos (`V12__singles_album.sql`)

No destructiva e idempotente en la práctica:

1. Reutilizar un álbum existente `name='Singles' AND artist IS NULL`;
   si no existe, insertarlo (`created_at = now()`). Si hubiera duplicados
   (la unique `(name, artist)` no protege los `NULL`), consolidarlos en el de
   menor `id` antes de crear el índice parcial.
2. `UPDATE tracks SET album = 'Singles', album_id = <id>`
   `WHERE album_id IS NULL AND (album IS NULL OR btrim(album) = '')`.
3. Para el resto de `album_id IS NULL` con `album` no vacío (datos antiguos):
   crear el álbum que falte por `(album, artist)` y enlazarlo. Hacerlo en SQL
   agrupado (`INSERT … SELECT DISTINCT …`) y un `UPDATE` posterior.
4. No tocar tags de audio en disco: la normalización se aplica al leer.

La migración se prueba con el `PostgresMigrationTest` (Testcontainers) y con
`@DataJpaTest` para el backfill.

### 4.4 Cambios de backend

| Archivo | Cambio |
|---|---|
| `scanner/LibraryCatalog.java` (nuevo) | Constantes + normalización + `isSingles`. |
| `scanner/LibrarySyncService` | Usa `LibraryCatalog` en `resolveAlbum`/`upsert`. |
| `scanner/IngestService` | `destinationFor` usa el nombre normalizado. |
| `service/MetadataEditService` | Álbum vacío en una pista ⇒ «Singles» (BD y tag). `updateAlbum` rechaza renombrar el álbum reservado con un `IllegalArgumentException` (400). |
| `repository/TrackRepository` | Orden determinista del tracklist: `trackNumber NULLS LAST, id` (los singles sin nº van al final). |
| `service/CoverArtService` | Singles **no hereda** portada de sus pistas ni consulta fuentes online: solo sirve una portada manual guardada en el álbum. Una pista sin arte propio devuelve el placeholder de no-carátula. |
| `dto/` | Sin cambios de contrato. |
| `config/OpenApiConfig` | Sin cambios (los endpoints no cambian). |

### 4.5 Cambios de frontend

| Archivo | Cambio |
|---|---|
| `pages/AlbumDetailPage.tsx` | Si `album.name === 'Singles'`, mostrar el artista de cada fila (`showArtist`), `kind="Singles"` en el hero y ocultar la edición de nombre (solo admin). |
| `components/AlbumCard.tsx` | Muestra «Varios artistas» como etiqueta cuando el álbum es Singles sin artista (el dato sigue siendo `NULL`). |
| `components/AlbumEditButton.tsx` | En Singles solo permite año/género: el nombre es reservado. |
| `pages/UploadPage.tsx` | Añadir accesos/instrucciones: «las canciones sueltas van a Singles». |
| Tests | `AlbumDetailPage` con álbum Singles (muestra artistas), card, y regresión de álbum normal. |

### 4.6 Casos límite y guardarraíles

- Archivo con `ALBUM` vacío y `ALBUM_ARTIST` presente → álbum Singles; la ruta
  física puede usar el `ALBUM_ARTIST` (comportamiento actual de `IngestService`).
- `ALBUM = "Single"`/`"Singles"` existente → se normaliza a «Singles» al
  escanear; sus pistas se reasignan en el siguiente `upsert`.
- Renombrar «Singles» desde la UI → rechazado con mensaje claro.
- Subir portada del álbum Singles → permitido (portada global de la colección).
- Favoritos de álbum/pista → funcionan sin cambios.
- Búsqueda `q` por «singles» → encuentra el álbum y sus pistas.

### 4.7 Criterios de aceptación

- [ ] Toda pista sin álbum aparece bajo un único álbum «Singles» tras migrar.
- [ ] Un archivo nuevo sin tag de álbum se ingiere en `<Artista>/Singles/` y se
      ve en la card «Singles».
- [ ] Un archivo con `ALBUM=Single` (singular) acaba en el mismo álbum.
- [ ] La página de «Singles» muestra el artista de cada pista.
- [ ] Álbumes normales intactos (nombre, artista, contador, portada).
- [ ] Tests backend y frontend verdes; CI verde.

---

## 5. Diseño — importación con yt-dlp

### 5.1 Dependencias en el servidor

YouTube exige, desde `yt-dlp 2025.11.12`, un runtime JavaScript externo para
resolver los retos del player. Plan:

1. `yt-dlp` **binario oficial** (zipimport/PyInstaller) en `/usr/local/bin`,
   que ya incluye `yt-dlp-ejs`. Evitar el paquete de Debian si va atrasado.
2. **Deno** (`>= 2.3`) en `/usr/local/bin` (runtime recomendado y habilitado por
   defecto). Alternativa: Node ≥ 22 con `--js-runtimes node`.
3. `ffmpeg` (ya debería estar; `setup.sh` lo instala).

Script nuevo `deploy/install-ytdlp.sh` (idempotente, root):

```bash
# descarga la última release de yt-dlp y deno, verifica versiones,
# y deja un `yt-dlp -U` documentado para actualizar.
```

Documentar en `docs/DEPLOY.md` y añadir el aviso de que **YouTube cambia a
menudo**: actualizar `yt-dlp` cuando fallen las descargas.

### 5.2 Configuración (`application.yml` / `application-prod.yml`)

```yaml
neonvibe:
  ytdlp:
    enabled: ${YTDLP_ENABLED:true}
    binary: ${YTDLP_PATH:yt-dlp}
    ffmpeg-path: ${FFMPEG_PATH:ffmpeg}
    js-runtime: ${YTDLP_JS_RUNTIME:deno}   # vacío = no pasar --js-runtimes
    audio-format: ${YTDLP_AUDIO_FORMAT:mp3}
    audio-quality: ${YTDLP_AUDIO_QUALITY:0}
    cookies-file: ${YTDLP_COOKIES:}        # opcional (vídeos con restricción)
    timeout-seconds: ${YTDLP_TIMEOUT:300}
    max-duration-seconds: ${YTDLP_MAX_DURATION:1800}
    max-concurrent: ${YTDLP_MAX_CONCURRENT:1}
```

`@ConfigurationProperties(prefix = "neonvibe.ytdlp")` en un
`YtDlpProperties`, registrado como los demás (`@ConfigurationPropertiesScan` o
`@EnableConfigurationProperties`).

### 5.3 Contrato API (admin)

Base: `/api/v1/admin/upload/youtube`

| Método | Ruta | Respuesta |
|---|---|---|
| `GET` | `/status` | `{enabled, available, version, js_runtime, js_runtime_available, message}` |
| `POST` | `/` | `202 {job_id, state}` · `400` URL inválida · `409` ocupado/no disponible |
| `GET` | `/jobs/{jobId}` | Estado y resultado del trabajo |

`POST` body:

```json
{ "url": "https://www.youtube.com/watch?v=XXXXXXXXXXX" }
```

`GET /jobs/{jobId}`:

```json
{
  "job_id": "…",
  "state": "DOWNLOADING",
  "stage": "Descargando audio…",
  "title": null,
  "artist": null,
  "album_id": null,
  "track_id": null,
  "error": null,
  "started_at": "…",
  "finished_at": null
}
```

Estados: `QUEUED → DOWNLOADING → TAGGING → INGESTING → SUCCEEDED | FAILED`.
Registro en memoria acotado (últimos ~20 trabajos, TTL 1 h) y **un solo trabajo
concurrente** (`409` si ya hay uno). Es suficiente para un servidor de un usuario;
si en el futuro se quiere persistencia, se migra a tabla.

### 5.4 Flujo interno

1. **Validar URL** (servidor): esquema `https`, host en
   `youtube.com`, `www.youtube.com`, `m.youtube.com`, `music.youtube.com`,
   `youtu.be`. Forzar un vídeo: se ignora `list=` (`--no-playlist`).
2. **Comprobar disponibilidad** (binario + runtime) → si falla, `503`/`409`
   accionable.
3. **Crear staging** `…/ytdlp-<uuid>` y lanzar:

```text
yt-dlp --no-config --no-cache-dir --no-playlist --no-progress --newline
       --write-info-json --write-thumbnail --convert-thumbnail jpg
       -f bestaudio/best -x --audio-format mp3 --audio-quality 0
       --ffmpeg-location <ffmpeg>
       [--js-runtimes <runtime>] [--cookies <file>]
       -o "<staging>/%(id)s.%(ext)s" "<url>"
```

4. **Leer `<id>.info.json`** (Jackson) y mapear metadatos (§5.6).
5. **Escribir tags** con `AudioTagWriter` (reutilizado) + carátula embebida
   (`<id>.jpg`) mediante una nueva sobrecarga `writeArtwork(Path, byte[])`.
6. **Renombrar** a `<Artista> - <Título>.mp3` y llamar a
   `IngestService.ingestUpload(staging)`, que ya organiza en `<Artista>/Singles/`
   y limpia el staging.
7. Devolver `track_id` y `album_id` (el de «Singles») para que la UI enlace.

El proceso se ejecuta en un `ThreadPoolTaskExecutor` dedicado (`ytdlpExecutor`,
1 hilo) para no bloquear requests ni el scanner.

### 5.5 Seguridad y hardening

- **Solo admin** (`AdminGuard`), igual que la subida actual.
- Lista blanca de hosts (evita SSRF a URLs arbitrarias).
- `--no-config` y `--no-cache-dir`: no lee configuración del sistema ni escribe
  en `$HOME` (que está bloqueado por `ProtectHome=true`).
- Timeout configurable y `destroyForcibly()` como en `DefaultFfmpegExecutor`.
- Salida de yt-dlp drenada en hilo daemon; el error que llega al cliente es un
  mensaje corto y traducido; el detalle completo va al log.
- Límite de duración (`max-duration-seconds`) para evitar vídeos de horas.
- `--no-playlist`: no descargar listas enteras por accidente.
- El staging se borra siempre (`finally`, como en la subida actual).
- Carátula: JPEG embebido (validar tamaño máximo, p. ej. 10 MB).

### 5.6 Mapeo de metadatos

| Origen (`info.json`) | Destino |
|---|---|
| `track` (si existe) o `title` | `TITLE` |
| `artist`, si no `uploader`, si no `channel` | `ARTIST` |
| `upload_date` (YYYYMMDD) | `YEAR` |
| — | `ALBUM = "Singles"` (forzado) |
| — | `ALBUM_ARTIST` vacío (para que la carpeta use el artista real) |
| `duration` | verificación opcional contra `max-duration` |

Nada de `list`/`playlist`. Si el artista no se puede determinar: `Unknown Artist`
(ya existe esa constante en el ingest).

### 5.7 UX en `/upload`

Nueva card «Importar desde YouTube» (solo admin):

- Input de URL con botón **Pegar** (`navigator.clipboard.readText()`, con
  fallback si el navegador lo deniega) y validación local básica.
- Botón **Importar** deshabilitado con URL vacía o trabajo en curso.
- Progreso por estados con texto claro: «Descargando audio…», «Etiquetando…»,
  «Añadiendo a la biblioteca…».
- Éxito: `«Título» añadido a Singles` + enlace «Ver Singles» (`/album/{album_id}`)
  y botón «Importar otro».
- Error: mensaje accionable (URL no válida, vídeo privado/eliminado, yt-dlp no
  disponible, timeout) con `role="alert"`.
- Si `/status` dice `available: false`: card en modo informativo con los pasos de
  instalación (sin romper el resto de la página).
- Polling con TanStack Query (`refetchInterval` ~1500 ms) mientras el trabajo
  esté en curso; se detiene al terminar.
- Al terminar con éxito: invalidar `['albums']`, `['album']`, `['tracks']`,
  `['home']` para que «Singles» aparezca al instante.

### 5.8 Errores esperados y mensajes

| Caso | HTTP | Mensaje UI |
|---|---|---|
| URL no YouTube / malformada | 400 | «Pega un enlace de YouTube válido.» |
| yt-dlp o runtime ausente | 409 | «La importación no está disponible en el servidor…» |
| Ya hay una importación | 409 | «Espera a que termine la importación actual.» |
| Vídeo privado/eliminado/geo | FAILED | «El vídeo no está disponible (privado, eliminado o restringido).» |
| Timeout | FAILED | «La descarga tardó demasiado. Inténtalo de nuevo.» |
| Fallo de ingest | FAILED | «Se descargó, pero no se pudo añadir a la biblioteca.» + log |

### 5.9 Criterios de aceptación

- [ ] Pegar un enlace válido crea un MP3 etiquetado en `<Artista>/Singles/` y
      aparece en la card «Singles» y en *Canciones*.
- [ ] Título, artista, año y carátula se rellenan desde YouTube.
- [ ] Un enlace con `&list=…` descarga **solo** el vídeo.
- [ ] URL de otro dominio → 400; sin yt-dlp → 409 con mensaje claro.
- [ ] Un segundo `POST` concurrente → 409 (no se solapan descargas).
- [ ] La UI refleja progreso, éxito y error sin recargar.
- [ ] Tests: validación de URL, construcción de comando, transiciones del job,
      mapeo de metadatos, `@WebMvcTest` del controller y tests de la card.

---

## 6. Bloques de trabajo

| Bloque | Nombre | Depende de | Esfuerzo |
|---|---|---|---|
| **S1** | `LibraryCatalog` + normalización en ingest/sync + tests | — | S |
| **S2** | Migración `V12__singles_album.sql` + tests de backfill | S1 | M |
| **S3** | Guardarraíles de metadata (álbum vacío ⇒ Singles; no renombrar) | S1 | S |
| **S4** | UI de álbum Singles (artistas por fila, hero) + tests | S2 | S |
| **Y1** | `YtDlpProperties` + ejecutor + `--version`/runtime + tests | — | M |
| **Y2** | `YouTubeImportService` (validación, job registry, comando) + tests | Y1 | L |
| **Y3** | Metadata/carátula + ingest + tests | Y2, S1 | M |
| **Y4** | Controller + DTOs + OpenAPI + `@WebMvcTest` | Y2 | M |
| **Y5** | Card de importación en `/upload` + polling + tests | Y4 | M |
| **Y6** | `deploy/install-ytdlp.sh`, docs de despliegue y env de ejemplo | Y1 | S |

**Orden recomendado:** S1 → S2 → S3 → S4 (Singles cerrado y desplegable) y en
paralelo Y1 → Y2 → Y3 → Y4 → Y5 → Y6. La importación depende de S1 para que el
audio acabe en el álbum correcto.

---

## 7. Plan de pruebas

### Backend

- Unitarios: `LibraryCatalog` (normalización), `LibrarySyncService` (álbum
  Singles, artista intacto), `IngestService.destinationFor` (ruta Singles),
  `MetadataEditService` (álbum vacío y rename bloqueado).
- `@DataJpaTest`: backfill de la migración (tracks sin álbum → Singles; tracks
  con álbum texto → álbum reconstruido).
- yt-dlp: URL válida/ inválida, construcción exacta del comando, mapeo de
  `info.json`, timeout, salida no cero, job registry (concurrencia, TTL).
- `@WebMvcTest`: `/status`, `POST` (202/400/409), `GET /jobs/{id}` (200/404) y
  verificación de `AdminGuard` (403 para no admin).
- Testcontainers: la migración V12 aplica sobre PostgreSQL real con
  `ddl-auto: validate`.

### Frontend

- `YouTubeImportCard`: render, URL inválida, pegar, progreso, éxito con enlace,
  error, estado «no disponible».
- `UploadPage`: la card aparece y el texto de Singles.
- `AlbumDetailPage`: álbum Singles muestra artista por fila; álbum normal no.
- `pnpm test`, `pnpm build` y `./mvnw test` verdes; actualizar contadores en
  `AGENTS.md`/`README.md`.

---

## 8. Despliegue

1. Instalar binarios (`deploy/install-ytdlp.sh`): `yt-dlp` + `deno` + verificar
   `ffmpeg`. El script crea `/var/lib/neonvibe/ytdlp` con owner `neonvibe`; si se
   instala a mano, hay que crearla o los jobs fallan con `AccessDeniedException`.
2. Añadir variables `YTDLP_*` a `/opt/neonvibe/neonvibe.env` (todas con default;
   solo ajustar si hace falta cookies o timeout).
3. Desplegar el JAR y reiniciar `neonvibe.service`.
4. Flyway aplica V12 al arrancar. **Hacer backup antes**:
   `sudo -u postgres pg_dump neonvibe > neonvibe-$(date +%F).sql`.
5. Comprobar:
   - `GET /api/v1/admin/upload/youtube/status` → `available: true`.
   - Importar un vídeo corto y verificar archivo + card «Singles».
   - Comprobar que el servicio reinicia sin errores (`journalctl -u neonvibe -n 100`).
6. Mantenimiento: actualizar `yt-dlp` (`yt-dlp -U`) cuando YouTube cambie; se
   puede automatizar con un `systemd timer` semanal (opcional).

---

## 9. Documentación a actualizar

- `AGENTS.md` §5 (modelo: álbum reservado «Singles»), §6 (endpoints YouTube),
  §10 (config `ytdlp`), §11 (v0.4), §14 (contadores de tests).
- `README.md` (características + roadmap v0.4).
- `CHANGELOG.md` (nueva feature).
- `docs/DEPLOY.md` (instalación y mantenimiento de yt-dlp/Deno).
- `docs/api/openapi.yaml` (regenerar desde `/v3/api-docs.yaml`).
- `deploy/neonvibe.env.example` (variables `YTDLP_*`).
- Este documento (§11 estado).

---

## 10. Riesgos y decisiones abiertas

| Riesgo / decisión | Impacto | Mitigación / propuesta |
|---|---|---|
| Un único «Singles» global vs por artista | UX y semántica | **Decidido: global sin artista** (`artist = NULL` + índice parcial). |
| yt-dlp se rompe cuando YouTube cambia | Importación caída | Detección de disponibilidad + mensaje claro + `yt-dlp -U` documentado. |
| Deno no instalado | Formatos limitados o fallo | `/status` avisa; instalar Deno en el script. |
| Reencode a MP3 | Pérdida de calidad vs opus/m4a nativo | Aceptable por compatibilidad; formato configurable. |
| Duplicados al reimportar el mismo vídeo | Basura en Singles | v1: `unique()` evita sobrescribir; v2: columna `source_url` + aviso. |
| Álbum «Singles» renombrable/borrable | Se rompe la reserva | Bloquear rename; no hay endpoint de borrado. |
| Migración sobre datos reales | Pérdida de datos | Backup + migración no destructiva + Testcontainers. |
| Límite de 100 MB de Cloudflare | No afecta: la descarga ocurre en el servidor | — |

**Decisiones del propietario (2026-11-06):**

- (a) **Singles global y sin artista** (`albums.artist = NULL`). La UI mostrará
  el artista de cada pista, pero el álbum no tendrá artista propio.
- (b) **Sí**: migrar automáticamente los 44 singles existentes con Flyway V12.
- (c) **MP3** (`--audio-format mp3`, calidad VBR 0).
- (d) **Solo vídeos sueltos** (`--no-playlist`).

**Hallazgos del diagnóstico de producción (2026-11-06):**

- `yt-dlp 2026.03.17` instalado **solo para `felipep`** en
  `/home/felipep/.local/bin`; el usuario `neonvibe` no lo ve.
- `deno` no instalado (necesario para YouTube desde yt-dlp 2025.11.12).
- `ffmpeg 7.1.5` disponible.
- 4577 pistas (4347 disponibles), **44 sin `album_id` y con tag de álbum vacío**,
  **0 álbumes** llamados «Single(s)». Todas las 44 irán a «Singles».
- Aviso: entre esas 44 hay pistas que en realidad pertenecen a un álbum pero
  perdieron el tag (p. ej. 6 de *Seasons End* de Marillion, *Faustian Echoes* de
  Agalloch). La migración las agrupará en «Singles»; se pueden corregir luego
  desde la UI de metadata sin tocar la migración.

---

## 11. Estado de ejecución

| Bloque | Estado | Fecha | Notas |
|---|---|---|---|
| Diagnóstico de código | Hecho | 2026-11-06 | Ver §2. |
| Diagnóstico de producción | Hecho | 2026-11-06 | 44 singles probables, 0 álbumes «Single(s)»; yt-dlp solo para `felipep`, sin Deno. |
| S1 | Hecho | 2026-11-06 | `LibraryCatalog` + normalización en sync/ingest; carátula lateral de singles va a la pista. |
| S2 | Hecho | 2026-11-06 | `V12__singles_album.sql` (backfill + consolidación + índice parcial). |
| S3 | Hecho | 2026-11-06 | Álbum vacío ⇒ Singles en edición; rename de/a Singles bloqueado. |
| S4 | Hecho | 2026-11-06 | Hero «Singles», artista por fila, card «Varios artistas», edición sin nombre. |
| Y1 | Hecho | 2026-11-06 | Propiedades, ejecutor `yt-dlp`/runtime, endpoint de estado. |
| Y2 | Hecho | 2026-11-06 | Servicio de jobs asíncrono, validación de URL, comando endurecido. |
| Y3 | Hecho | 2026-11-06 | Metadatos desde `info.json`, carátula embebida, ingesta en Singles. |
| Y4 | Hecho | 2026-11-06 | Controller `/admin/upload/youtube` + DTOs + tests HTTP. |
| Y5 | Hecho | 2026-11-06 | Card en `/upload` con pegar/progreso/éxito/error y enlace a Singles. |
| Y6 | Hecho | 2026-11-06 | `deploy/install-ytdlp.sh`, `setup.sh`, env de ejemplo y DEPLOY. |
| Despliegue | Hecho | 2026-11-06 | V12/V13 aplicadas, `yt-dlp` + Deno instalados, importación de YouTube validada en producción. |

**Verificación (2026-11-06):** `./mvnw test` → **618 tests** (0 fallos, 4
skipped, JaCoCo OK); `pnpm test` → **359 tests**; `pnpm build` OK.

**Notas de implementación:**

- La migración V12 también consolida posibles duplicados del álbum global y
  normaliza tags `Single`/`Singles` existentes.
- **V13** limpia la portada que el álbum «Singles» hubiera heredado de una pista
  (`cover_art_path`/`cover_fetched_at`). La resolución de carátulas ya no deriva
  una portada de colección desde las pistas ni consulta iTunes/MusicBrainz/Last.fm;
  solo se sirve una portada subida manualmente. Las filas de singles sin arte
  propio muestran el placeholder de no-carátula.
- El frontend bumpa `COVER_CACHE_VERSION` (2) en `api/cover.ts` para invalidar las
  imágenes cacheadas 24 h por los navegadores.
- Las carátulas laterales (`cover.jpg`) de un single se guardan **por pista**
  (`CoverArtService.saveManualTrack`), no en el álbum global.
- El importador descarga a un directorio temporal, escribe los tags con
  jaudiotagger, embebe la miniatura y llama a `IngestService.ingestSingle`.
- La comprobación de disponibilidad se cachea en el cliente 60 s; el job se
  consulta cada 1,5 s hasta `SUCCEEDED`/`FAILED`.
- El fallo de arranque detectado en pruebas (dos `ExecutorService` en el
  contexto) se resolvió cualificando `scannerExecutor` en `FileWatcherService`.
- `--ffmpeg-location` solo se pasa con rutas reales (absolutas o con `/`); con un
  nombre simple se omite para que yt-dlp resuelva `ffmpeg` **y `ffprobe`** desde
  el `PATH` (pasar `ffmpeg` a secas hacía que fallara el postprocesado).
- `DefaultYtDlpExecutor` fija `DENO_DIR` dentro del staging porque el servicio
  corre con `ProtectHome=true` y Deno no puede escribir en `$HOME/.cache`.
- **Ingesta concurrente (watcher/escáner/import a la vez):** `resolveArtist` y
  `resolveAlbum` insertan con `ON CONFLICT DO NOTHING` y releen, en lugar de un
  `save` que abortaba la transacción con `uq_artists_name`. `IngestService`
  reintenta el upsert de la pista si otro proceso gana la carrera de
  `file_path`.
