# Fase 2 — Dominio Musical y Scanner: Especificación Técnica

> Versión: 1.0 — 2026-08-06
> Propietario: Subagente Fase 2
> Estado: Aprobada (base para la implementación)
> Dependencias: Fase 0 (bootstrap) y Fase 1 (auth, entidad `User`, JWT)

---

## 1. Objetivo

Hacer que el sistema entienda la biblioteca musical. Implementar las entidades JPA del
dominio musical (`Track`, `Album`, `Artist`, `Playlist`, `PlaylistTrack`, `PlayQueue`,
`PlayHistory`, `Favorite`), sus repositorios, la migración Flyway `V3__music_schema.sql`,
un scanner de archivos (WatchService + escaneo periódico de respaldo) que extrae
metadatos y sincroniza la base de datos, y la API REST de lectura/gestión sobre ese
dominio. Todo expuesto a través de DTOs (nunca entidades JPA).

## 2. Alcance

**Incluye:**

- Entidades JPA de dominio (8) con relaciones, índices y cascadas definidos.
- Migración Flyway `V3__music_schema.sql` con todas las tablas.
- Repositorios Spring Data JPA con métodos de búsqueda derivados.
- Pipeline de extracción de metadatos (jaudiotagger) + fallback por nombre de archivo.
- Scanner: `WatchService` (java.nio) como mecanismo principal + escaneo periódico de
  respaldo con `ScheduledExecutorService`.
- `LibrarySyncService`: upsert de Track/Album/Artist, borrado lógico de archivos
  desaparecidos.
- Controllers REST (DTOs) para tracks, álbumes, artistas, playlists, queue, history,
  favorites y admin/scan.
- Tests (mínimo 5 clases): metadata extractor, repos, controllers, scanner service.

**Excluye:**

- Streaming de audio (`GET /tracks/{id}/stream` con HTTP Range) → Fase 3.
- WebSocket para sync de reproductor → Fase 3.
- Descarga/gestión de carátulas online y letras → Fase 8.
- Cualquier código frontend.

---

## 3. Modelo de Datos (Entidades JPA)

Convenciones transversales:

- IDs: `Long` auto-increment (`GenerationType.IDENTITY`) salvo `User.id` (UUID, Fase 1).
- Timestamps: `Instant` mapeado a `timestamptz` en PostgreSQL. Se usan
  `@CreationTimestamp` y `@UpdateTimestamp` de Hibernate (no callbacks manuales), salvo
  en `User` (Fase 1 ya definido con `@PrePersist`/`@PreUpdate`).
- Nombres de tabla/columna: `snake_case`.
- `lazy` por defecto para colecciones; `fetch = LAZY` explícito.
- Lombok: `@Getter @Setter @NoArgsConstructor` (y `@Builder`/`@AllArgsConstructor` donde
  aporte).

### 3.1 Track

| Campo | Tipo | Constraints | Notas |
|-------|------|-------------|-------|
| id | Long IDENTITY | PK | |
| filePath | varchar | NOT NULL, UNIQUE | Ruta absoluta del archivo |
| title | varchar(500) | NOT NULL | Título (fallback: nombre de archivo) |
| artist | varchar(500) | NULL | Denormalizado |
| album | varchar(500) | NULL | Denormalizado (nombre) |
| albumArtist | varchar(500) | NULL | Denormalizado |
| year | Integer | NULL | |
| genre | varchar(255) | NULL | |
| trackNumber | Integer | NULL | |
| discNumber | Integer | NULL | |
| durationSeconds | Integer | NULL | |
| bitrate | Integer | NULL | kbps |
| format | varchar(20) | NULL | Extensión: mp3, flac, … |
| mimeType | varchar(100) | NULL | Deriva de extensión |
| hasLyrics | boolean | NOT NULL default false | |
| coverArtPath | varchar(1000) | NULL | Ruta local de carátula cacheada (Fase 8) |
| isAvailable | boolean | NOT NULL default true | Soft-delete cuando el archivo desaparece |
| createdAt | timestamptz | NOT NULL | |
| updatedAt | timestamptz | NOT NULL | |
| album (fk) | Long | FK → albums.id, NULL | ManyToOne |
| artist (fk) | Long | FK → artists.id, NULL | ManyToOne |

Índices: `idx_track_artist`, `idx_track_album`, `idx_track_genre`, `idx_track_title`,
`idx_track_year`; índice único `uq_track_file_path`.

Relación con `Album`: `@ManyToOne(fetch = LAZY, optional = true)`; sin cascada desde
Track hacia Album (la creación de Album la gestiona `LibrarySyncService`).
Relación con `Artist`: igual.

### 3.2 Album

| Campo | Tipo | Constraints |
|-------|------|-------------|
| id | Long IDENTITY | PK |
| name | varchar(500) | NOT NULL |
| artist | varchar(500) | NULL (denormalizado) |
| year | Integer | NULL |
| genre | varchar(255) | NULL |
| coverArtPath | varchar(1000) | NULL |
| createdAt | timestamptz | NOT NULL |

- `@OneToMany(mappedBy = "album") List<Track> tracks`, `orphanRemoval = false`
  (los tracks que quedan "huérfanos" del álbum se reasocian/no eliminan).
- Índices: `uq_album_name_artist` (name, artist) único suave; `idx_album_artist`,
  `idx_album_name`.

### 3.3 Artist

| Campo | Tipo | Constraints |
|-------|------|-------------|
| id | Long IDENTITY | PK |
| name | varchar(500) | NOT NULL, UNIQUE |
| createdAt | timestamptz | NOT NULL |

- `@OneToMany(mappedBy = "artist")` colecciones opcionales de albums/tracks; la
  sincronización real se hace vía queries en `LibrarySyncService` (evitar cargar
  colecciones grandes).

### 3.4 Playlist

| Campo | Tipo | Constraints |
|-------|------|-------------|
| id | Long IDENTITY | PK |
| userId | uuid | FK → users.id, NOT NULL |
| name | varchar(200) | NOT NULL |
| description | varchar(2000) | NULL |
| isPublic | boolean | NOT NULL default false |
| coverArtPath | varchar(1000) | NULL |
| createdAt | timestamptz | NOT NULL |
| updatedAt | timestamptz | NOT NULL |

- `@ManyToOne(fetch = LAZY) User user` → `user_id`.
- `@OneToMany(mappedBy = "playlist", cascade = ALL, orphanRemoval = true)
  List<PlaylistTrack> tracks`.
- Índices: `idx_playlist_user`, `idx_playlist_is_public`.

### 3.5 PlaylistTrack (join con orden)

| Campo | Tipo | Constraints |
|-------|------|-------------|
| id | Long IDENTITY | PK |
| playlistId | Long | FK → playlists.id, NOT NULL |
| trackId | Long | FK → tracks.id, NOT NULL |
| position | Integer | NOT NULL (empieza en 0) |

- Uniqueness: `(playlist_id, position)` y `(playlist_id, track_id)`.

### 3.6 PlayQueue

| Campo | Tipo | Constraints |
|-------|------|-------------|
| id | Long IDENTITY | PK |
| userId | uuid | FK → users.id, NOT NULL, UNIQUE |
| currentTrackId | Long | FK → tracks.id, NULL |
| positionSeconds | Integer | NOT NULL default 0 |
| shuffleEnabled | boolean | NOT NULL default false |
| repeatMode | varchar(10) | NOT NULL default 'NONE' (enum) |
| tracksOrder | text | NULL (JSON array) |
| updatedAt | timestamptz | NOT NULL |

- Un queue por usuario: `user_id` UNIQUE.
- `tracksOrder`: se decide **JSON string** (no CSV) porque JSON es la representación
  nativa de la cola y el frontend trabajará con arrays; se validará con Jackson.

### 3.7 PlayHistory

| Campo | Tipo | Constraints |
|-------|------|-------------|
| id | Long IDENTITY | PK |
| userId | uuid | FK → users.id, NOT NULL |
| trackId | Long | FK → tracks.id, NOT NULL |
| playedAt | timestamptz | NOT NULL |
| completed | boolean | NOT NULL default false |
| durationListenedSeconds | Integer | NULL |

- Índices: `idx_history_user_played` (user_id, played_at DESC).

### 3.8 Favorite

| Campo | Tipo | Constraints |
|-------|------|-------------|
| id | Long IDENTITY | PK |
| userId | uuid | FK → users.id, NOT NULL |
| entityType | varchar(10) | NOT NULL (enum: TRACK/ALBUM/ARTIST) |
| entityId | Long | NOT NULL |
| createdAt | timestamptz | NOT NULL |

- Uniqueness: `(user_id, entity_type, entity_id)`.

### 3.9 Enums

- `RepeatMode`: `NONE`, `ALL`, `ONE`.
- `FavoriteEntityType`: `TRACK`, `ALBUM`, `ARTIST`.
- **Almacenamiento:** se persisten como `VARCHAR` (con `@Enumerated(EnumType.STRING)`),
  no como PostgreSQL ENUM, para maximizar compatibilidad entre PostgreSQL y H2 (H2 no
  soporta nativamente los ENUM de PostgreSQL) en los tests.

---

## 4. Esquema de Base de Datos (Flyway V3)

La migración `V3__music_schema.sql` crea las 8 tablas con:

- PKs (identity para las nuevas, uuid para la referencia a `users`).
- FKs definidas y referenciadas a `users.id` (uuid), `tracks.id`, `playlists.id`.
- Índices de búsqueda (artist, album, genre, title, year) en `tracks`.
- Constraints constrs y unique.
- `TIMESTAMPTZ` para todos los timestamps.
- Nombres de columnas exactos según las entidades JPA (para que `ddl-auto: validate`
  pase con el mismo esquema).

Nota sobre `V2`: en el spec de Fase 1 se dijo que las columnas `user_id` se añadirían en
las migraciones de Fase 2. `V3` las crea junto con las tablas que las usan (no modifica
`users`).

Migración ordenada (Flyway ejecuta en orden de versión): `V1` (placeholder) → `V2`
(users) → `V3` (music schema, esta fase).

---

## 5. Arquitectura del Scanner

### 5.1 Enfoque: WatchService + poll periódico de respaldo

- **Mecanismo primario:** `java.nio.file.WatchService` registra `ENTRY_CREATE`,
  `ENTRY_DELETE`, `ENTRY_MODIFY` en cada directorio de la raíz configurada. Es
  eficiente (notificaciones del kernel) y es la recomendación de AGENTS.md
  (inotify en Linux).
- **Problema:** `WatchService` NO es recursivo. Debe recorrerse el árbol al arrancar y
  registrar un watcher por directorio. Además, eventos intermedios de mover/renombrar
  pueden perderse y algunos sistemas de archivos (NFS, montajes) no emiten eventos.
- **Solución combinada:**
  1. **Escaneo inicial** al arrancar: recorre el árbol de la raíz y registra watchers
     por directorio; sincroniza la biblioteca.
  2. **WatchService** para cambios incrementales en tiempo real.
  3. **Escaneo periódico de respaldo** (`ScheduledExecutorService`) que re-escanea el
     árbol completo a un intervalo configurable (por defecto `0` = desactivado, solo
     WatchService + manual). Se reactiva para corregir eventos perdidos y archivos
     renombrados.

### 5.2 Hilos y async (no bloquear la app)

- `MusicScannerService` es el orquestador `@Service`.
- La extracción de metadatos y el upsert en DB se ejecutan en un
  `ExecutorService` (thread pool con un `ThreadPoolExecutor` con tamaño acotado,
  p. ej. maxPoolSize configurable, default 2). Los eventos de WatchService se
  encolan a ese pool para no bloquear el hilo del watcher.
- Nada de escaneo se ejecuta sobre el hilo de las requests HTTP.

### 5.3 Pipeline de extracción

```
Archivo detectado (path + evento)
   │
   ├─ ¿Extensión soportada?  ── no ──► descartar
   │  sí
   ▼
MetadataExtractor.extract(path)   ──► Metadata { title, artist, album, albumArtist,
   │                                       year, genre, trackNumber, discNumber,
   │                                       durationSeconds, bitrate, format, mimeType }
   └─ tags vacíos ──► fallback: parsear nombre de archivo ("Artist - Title", "NN - Title")
   ▼
LibrarySyncService.upsert(metadata, path)
   ├─ getOrCreate Artist por name
   ├─ getOrCreate Album por (name, artist)
   ├─ upsert Track por filePath (crea o actualiza si cambió)
   └─ si archivo borrado: Track.isAvailable = false (soft delete)
```

Los errores de un archivo (metadata corrupta, archivo eliminado a mitad de lectura) se
capturan por track, se loguean y se registran en un mapa de "archivos fallidos" en
memoria (consultable por `GET /admin/scan/status`), sin derrumbar la app.

---

## 6. Extracción de Metadatos

### 6.1 Librería elegida: jaudiotagger 3.0.1

`net.jthink:jaudiotagger:3.0.1` (última publicada en Maven Central).

**Justificación:**

- Es la librería Java estándar y más usada para edición/lectura de tags de audio
  (usada por Jaikoz/SongKong).
- Soporta MP3 (ID3v2), MP4/M4A, OGG (Vorbis comments), FLAC, WAV y más.
- Madura, activa y disponible en Maven Central. No requiere dependencias nativas extra.
- Alternativas consideradas y descartadas: Apache Tika (muy pesado, orientado a
  detección de tipos de muchos formatos, no tan especializado en tags de audio),
  `musicmetadata` (obsoleto). `jaudiotagger` es el mejor equilibrio para un servidor de
  música.

### 6.2 Campos que se extraen

| Campo JPA | Tag jaudiotagger (ID3v2) | Vorbis (OGG/FLAC) |
|-----------|--------------------------|-------------------|
| title | TIT2 | TITLE |
| artist | TPE1 | ARTIST |
| album | TALB | ALBUM |
| albumArtist | TPE2 | ALBUMARTIST |
| year | TDRC (año) | DATE |
| genre | TCON | GENRE |
| trackNumber | TRCK | TRACKNUMBER |
| discNumber | TPOS | DISCNUMBER |
| durationSeconds | AudioHeader.getTrackLength() | (idem) |
| bitrate | AudioHeader.getBitRate() | (idem) |
| format / mimeType | derivado de extensión | idem |

- `hasLyrics`: se marca `true` si el archivo contiene una letra incrustada o si se
  decide (futuro) tras consulta. En esta fase se deja en `false` por defecto a menos que
  el tag contenga letra (USLT/UNSYNCEDLYRICS).

### 6.3 Manejo de tags faltantes

- Si un campo de texto está vacío, se deja en `null` (columnas nullable).
- `title` es NOT NULL → se rellena con fallback por nombre de archivo
  (sin extensión), normalizado.
- `artist`/`album` vacíos → quedan `null`; el Album/Artist denormalizado solo se crea
  si hay nombre presente (evitar álbumes/artistas vacíos).
- Parsing de nombre de archivo como fallback: patrones comunes
  `"Artista - Título.ext"`, `"NN - Título.ext"`, `"Título.ext"`.

---

## 7. API REST

Base: `/api/v1`. Todos los endpoints bajo `/api/**` requieren JWT (SecurityConfig Fase
1). JSON responde en `snake_case` (Jackson SNAKE_CASE ya configurado). Errores con el
contrato estándar `{error, message, timestamp}` vía `GlobalExceptionHandler`.

### 7.1 Tracks

| Método | Ruta | Query params | Códigos | Descripción |
|--------|------|--------------|---------|-------------|
| GET | `/api/v1/tracks` | `q`, `artist`, `album`, `genre`, `year`, `page`, `size`, `sort` | 200 | Lista paginada, filtrable, busca por título |
| GET | `/api/v1/tracks/{id}` | — | 200 / 404 | Detalle de track |

- `q`: búsqueda `title` insensitive (contains). `artist`/`album`/`genre`/`year`: filtros
  exactos (contiene para textos). `page`/`size`: paginación de Spring (defecto page=0,
  size=20, máximo 100). `sort`: p. ej. `title,asc`.

### 7.2 Albums

| Método | Ruta | Query | Códigos | Descripción |
|--------|------|-------|---------|-------------|
| GET | `/api/v1/albums` | `q`, `artist`, `page`, `size` | 200 | Lista paginada |
| GET | `/api/v1/albums/{id}` | — | 200 / 404 | Detalle |
| GET | `/api/v1/albums/{id}/tracks` | `page`, `size` | 200 / 404 | Tracks del álbum |

### 7.3 Artists

| Método | Ruta | Query | Códigos | Descripción |
|--------|------|-------|---------|-------------|
| GET | `/api/v1/artists` | `q`, `page`, `size` | 200 | Lista paginada |
| GET | `/api/v1/artists/{id}` | — | 200 / 404 | Detalle |
| GET | `/api/v1/artists/{id}/albums` | — | 200 / 404 | Álbumes del artista |
| GET | `/api/v1/artists/{id}/tracks` | — | 200 / 404 | Tracks del artista |

### 7.4 Playlists (por usuario)

| Método | Ruta | Códigos | Descripción |
|--------|------|---------|-------------|
| GET | `/api/v1/playlists` | 200 | Mis playlists + públicas |
| POST | `/api/v1/playlists` | 201 / 400 | Crear |
| PUT | `/api/v1/playlists/{id}` | 200 / 404 / 403 | Actualizar (solo owner) |
| DELETE | `/api/v1/playlists/{id}` | 204 / 404 / 403 | Eliminar (solo owner) |
| POST | `/api/v1/playlists/{id}/tracks` | 201 | Añadir track |
| DELETE | `/api/v1/playlists/{id}/tracks/{trackId}` | 204 / 404 | Quitar track |
| POST | `/api/v1/playlists/{id}/reorder` | 200 | Reordenar (lista de trackIds) |

- Body: `PlaylistRequest { name, description, isPublic }`;
  `PlaylistTrackRequest { trackId }`; `ReorderRequest { trackIds: [] }`.

### 7.5 Queue (por usuario)

| Método | Ruta | Códigos | Descripción |
|--------|------|---------|-------------|
| GET | `/api/v1/queue` | 200 | Estado actual de la cola (`PlayQueueResponse`) |
| PUT | `/api/v1/queue` | 200 / 400 | Actualizar cola completa (`PlayQueueRequest`) |

- Solo 1 cola por usuario (upsert por `user_id`).

### 7.6 History (por usuario)

| Método | Ruta | Códigos | Descripción |
|--------|------|---------|-------------|
| GET | `/api/v1/history` | 200 | Historial paginado del usuario |
| POST | `/api/v1/history` | 201 / 400 | Registrar reproducción |

- `HistoryRequest { trackId, completed, durationListenedSeconds }`; `playedAt` se fija
  en servidor (Instant.now()).

### 7.7 Favorites (por usuario)

| Método | Ruta | Códigos | Descripción |
|--------|------|---------|-------------|
| GET | `/api/v1/favorites` | 200 | Lista de favoritos (filtrable por `entityType`) |
| POST | `/api/v1/favorites` | 201 / 400 / 409 | Añadir favorito |
| DELETE | `/api/v1/favorites/{id}` | 204 / 404 | Quitar favorito |

- `FavoriteRequest { entityType, entityId }`.

### 7.8 Admin / Scanner

| Método | Ruta | Códigos | Descripción |
|--------|------|---------|-------------|
| POST | `/api/v1/admin/scan` | 202 | Trigger scan manual asíncrono |
| GET | `/api/v1/admin/scan/status` | 200 | Estado del scanner (estado, total, procesados, fallidos) |

### 7.9 Extracción de usuario autenticado

Los controllers que necesitan `user_id` (playlists, queue, history, favorites) obtienen
el `UserPrincipal` del `SecurityContext` (ya poblado por `JwtAuthenticationFilter` de
Fase 1) y usan `principal.id()`. No se filtra por header/body manual.

---

## 8. Diseño de DTOs y Mapeo

### 8.1 DTOs

Todos en `com.neonvibe.dto`, como `record`s inmutables (patrón ya usado en Fase 1):

- **Track:** `TrackResponse`, `TrackRequest`.
- **Album:** `AlbumResponse`, `AlbumRequest`.
- **Artist:** `ArtistResponse`.
- **Playlist:** `PlaylistResponse`, `PlaylistRequest`, `PlaylistTrackRequest`, `ReorderRequest`.
- **Queue:** `PlayQueueResponse`, `PlayQueueRequest`.
- **History:** `PlayHistoryResponse`, `PlayHistoryRequest`.
- **Favorite:** `FavoriteResponse`, `FavoriteRequest`.
- **Scanner:** `ScanStatusResponse`.

`TrackRequest` no es expuesto vía POST directo (los tracks los crea el scanner), pero se
define por completitud del contrato DTO según el modelo (búsqueda/usos futuros). Los
request que reciben body se validan con `@Valid` + `jakarta.validation` constraints
(`@NotBlank`, `@Size`, `@NotNull`, etc.).

### 8.2 Mapeo: MapStruct vs manual

**Decisión:** usar **MapStruct 1.6.3** con beans del `com.neonvibe.mapper` package,
siguiendo el setup ya presente en `pom.xml` (mapstruct-processor + lombok-mapstruct-
binding ya configurado en Fase 0). MapStruct genera mapeadores en compilación, es más
mantenible que mapeos manuales repetidos y ya está declarado en el pom.

**Plan de contingencia (documentado):** si el binding MapStruct+Lombok generara
problemas de compilación (p. ej. `Encountered a recursive/implicit reference` o no
generación de impls), se usará mappers manuales estáticos (patrón
`private static X toY(...)`), que son igual de válidos y sin riesgo de anotación
processing. Esta elección se documentará en la review.

Los mappers son interfaces `@Mapper(componentModel = "spring")` inyectadas por
constructor en los servicios. Mapean Entidad → DTO y DTO → Entidad (donde corresponda,
p. ej. PlaylistRequest → Playlist).

---

## 9. Manejo de Errores del Scanner

- `GlobalExceptionHandler` (Fase 0) sigue manejando errores HTTP de controllers
  (404 `ResourceNotFoundException`, 400 validación, 500 genérico, 401 auth).
- Los errores **internos del scanner** (un archivo corrupto, metadata ilegible) NO se
  propagan al cliente: se capturan por track en `MusicScannerService`, se loguean con
  `warn/error`, y el archivo se registra en un mapa `failedFiles` en memoria
  (path → mensaje + timestamp), visible en `StatusResponse`. Un fallo de un archivo
  jamás derriba el hilo del scanner ni la app.
- Se añade una excepción de dominio opcional `ScanException` (runtime) para casos
  graves del scanner (p. ej. raíz configurada inaccesible), que el orquestador captura
  y registra como estado del scanner sin afectar a la app.

---

## 10. Configuración (application.yml)

Se añade la sección `neonvibe.music` (ya esbozada en Fase 0), externalizada y
especificada por `ScannerConfig` (`@ConfigurationProperties` prefijo `neonvibe.music`):

```yaml
neonvibe:
  music:
    paths: /srv/Music
    supported-formats: mp3,flac,aac,ogg,m4a,wav
    scan-interval-seconds: 0      # 0 = solo WatchService + escaneo inicial/manual
    max-file-size-mb: 500         # opcional, ignorar archivos sospechosamente grandes (futuro)
```

- `application.yml`: `paths: /srv/Music` (default de producción, según AGENTS.md).
- `application-dev.yml`: `paths: /tmp/test-music` para desarrollo local.
- `ScannerConfig` habilita `@ConfigurationProperties(prefix = "neonvibe.music")`.

---

## 11. Lista de Archivos a Crear/Modificar

### Crear (main) — `neonvibe/backend/src/main/java/com/neonvibe/`

```
domain/           Track, Album, Artist, Playlist, PlaylistTrack, PlayQueue,
                  PlayHistory, Favorite, RepeatMode, FavoriteEntityType
repository/       TrackRepository, AlbumRepository, ArtistRepository,
                  PlaylistRepository, PlaylistTrackRepository, PlayQueueRepository,
                  PlayHistoryRepository, FavoriteRepository
dto/              TrackResponse, TrackRequest, AlbumResponse, AlbumRequest,
                  ArtistResponse, PlaylistResponse, PlaylistRequest,
                  PlaylistTrackRequest, ReorderRequest, PlayQueueResponse,
                  PlayQueueRequest, PlayHistoryResponse, PlayHistoryRequest,
                  FavoriteResponse, FavoriteRequest, ScanStatusResponse
mapper/           TrackMapper, AlbumMapper, ArtistMapper, PlaylistMapper,
                  PlayQueueMapper, PlayHistoryMapper, FavoriteMapper
scanner/          MusicScannerService, FileWatcherService, MetadataExtractor (interfaz),
                  JAudioTaggerMetadataExtractor, LibrarySyncService, ScannerConfig,
                  ScannerStatus, ScanException, MusicMetadata (record)
service/          TrackService, AlbumService, ArtistService, PlaylistService,
                  PlayQueueService, PlayHistoryService, FavoriteService, ScanService
controller/       TrackController, AlbumController, ArtistController,
                  PlaylistController, QueueController, HistoryController,
                  FavoriteController, AdminController
```

### Crear (resources)

```
neonvibe/backend/src/main/resources/db/migration/V3__music_schema.sql
```

### Modificar

```
neonvibe/backend/pom.xml                        (añadir jaudiotagger 3.0.1)
neonvibe/backend/src/main/resources/application.yml    (sección neonvibe.music refinada)
neonvibe/backend/src/main/resources/application-dev.yml (paths: /tmp/test-music)
```

### Crear (test) — `neonvibe/backend/src/test/java/com/neonvibe/`

```
scanner/MetadataExtractorTest.java
scanner/MusicScannerServiceTest.java
repository/TrackRepositoryTest.java
controller/TrackControllerTest.java
service/PlaylistServiceTest.java
```

### Documentación

```
neonvibe/docs/specs/fase-2-scanner-spec.md   (este archivo)
neonvibe/docs/plans/fase-2-scanner-plan.md   (plan)
neonvibe/docs/reviews/fase-2-scanner-review.md (review)
```

---

## 12. Criterios de Aceptación

1. `./mvnw clean compile` — 0 errores.
2. `./mvnw test` — todas las pruebas pasan (mínimo 5 clases de test).
3. Entidades JPA con relaciones, cascadas e índices correctos; `ddl-auto: validate`
   consistente con `V3__music_schema.sql`.
4. Scanner compila, es async (no bloquea requests) y detecta archivos vía WatchService
   + escaneo inicial + poll opcional.
5. `GET /tracks` devuelve paginación con filtros (q/artist/album/genre/year).
6. Endpoints de queue/history/favorites/playlists filtran por `user_id` del JWT.
7. Solo DTOs expuestos en controllers; constructor injection obligatoria.
8. Errores de scanner se loguean por archivo; la app no se cae.
9. Metadatos extraídos de MP3 (title, artist, album, year, genre, track_number).
10. Archivo borrado → track marcado como no disponible (soft delete).
