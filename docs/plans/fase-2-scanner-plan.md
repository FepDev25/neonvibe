# Fase 2 — Dominio Musical y Scanner: Plan de Implementación

> Versión: 1.0 — 2026-08-06
> Propietario: Subagente Fase 2
> Base: spec `docs/specs/fase-2-scanner-spec.md`
> Dependencias: Fase 0 (bootstrap) y Fase 1 (auth/User/JWT)

---

## 1. Orden de implementación (topológico)

Para minimizar fallos de compilación, los artefactos se crean en orden de dependencia:

1. **Migración Flyway** `V3__music_schema.sql` (contrato de esquema).
2. **Enums** (`RepeatMode`, `FavoriteEntityType`) — no dependen de nada.
3. **Entidades JPA** (8) — dependen de enums y de `User` (Fase 1).
4. **Repositorios** (8) — dependen de entidades.
5. **DTOs** (records) — solo librerías standard.
6. **Mappers** (MapStruct) — dependen de entidades + DTOs.
7. **Dependencia** `jaudiotagger` en `pom.xml`.
8. **Scanner** (config, metadata record, extractor interface + impl, sync service,
   watcher, orquestador, status).
9. **Servicios de negocio** (track, album, artist, playlist, queue, history, favorite,
   scan).
10. **Controllers** REST.
11. **Config** `application.yml` / `application-dev.yml`.
12. **Tests.**

Cada sub-bloque se compila antes de avanzar (checkpoints de testing).

---

## 2. Secuencia detallada de archivos

### Fase 2.1 — Migración y entidades

```
db/migration/V3__music_schema.sql
domain/RepeatMode.java
domain/FavoriteEntityType.java
domain/Track.java
domain/Album.java
domain/Artist.java
domain/Playlist.java
domain/PlaylistTrack.java
domain/PlayQueue.java
domain/PlayHistory.java
domain/Favorite.java
```
**Checkpoint:** `./mvnw -q compile` compila. (Aún sin repos, pero las entidades
referencian `User` ya existente.)

### Fase 2.2 — Repositorios

```
repository/TrackRepository.java          findByFilePath, findByArtistContainingIgnoreCase,
                                         findByAlbumContainingIgnoreCase,
                                         findByGenreContainingIgnoreCase,
                                         findByTitleContainingIgnoreCase, findByYear,
                                         findByArtistId, findByAlbumId, existsByFilePath,
                                         findAllByIsAvailableTrue
repository/AlbumRepository.java          findByNameAndArtist, findByNameContainingIgnoreCase
repository/ArtistRepository.java         findByName, findByNameContainingIgnoreCase
repository/PlaylistRepository.java       findByUserId, findByUserIdAndIsPublic
repository/PlaylistTrackRepository.java  findByPlaylistIdOrderByPosition,
                                         deleteByPlaylistId, existsByPlaylistIdAndTrackId
repository/PlayQueueRepository.java      findByUserId
repository/PlayHistoryRepository.java    findByUserIdOrderByPlayedAtDesc
repository/FavoriteRepository.java       findByUserId, existsByUserIdAndEntityTypeAndEntityId
```

### Fase 2.3 — DTOs

```
dto/TrackResponse.java, dto/TrackRequest.java
dto/AlbumResponse.java, dto/AlbumRequest.java
dto/ArtistResponse.java
dto/PlaylistResponse.java, dto/PlaylistRequest.java,
dto/PlaylistTrackRequest.java, dto/ReorderRequest.java
dto/PlayQueueResponse.java, dto/PlayQueueRequest.java
dto/PlayHistoryResponse.java, dto/PlayHistoryRequest.java
dto/FavoriteResponse.java, dto/FavoriteRequest.java
dto/ScanStatusResponse.java
```

### Fase 2.4 — Mappers (MapStruct)

```
mapper/TrackMapper.java
mapper/AlbumMapper.java
mapper/ArtistMapper.java
mapper/PlaylistMapper.java
mapper/PlayQueueMapper.java
mapper/PlayHistoryMapper.java
mapper/FavoriteMapper.java
```

### Fase 2.5 — Dependencia + Scanner

- `pom.xml`: añadir `net.jthink:jaudiotagger:3.0.1`.
- `scanner/ScannerConfig.java` (`@ConfigurationProperties("neonvibe.music")`).
- `scanner/MusicMetadata.java` (record con todos los campos extraídos).
- `scanner/MetadataExtractor.java` (interfaz).
- `scanner/JAudioTaggerMetadataExtractor.java` (impl).
- `scanner/LibrarySyncService.java` (upsert).
- `scanner/FileWatcherService.java` (WatchService recursivo + cola a pool).
- `scanner/ScannerStatus.java` (estado en memoria).
- `scanner/ScanException.java`.
- `scanner/MusicScannerService.java` (orquestador).
**Checkpoint:** compilar.

### Fase 2.6 — Servicios de negocio

```
service/TrackService.java
service/AlbumService.java
service/ArtistService.java
service/PlaylistService.java
service/PlayQueueService.java
service/PlayHistoryService.java
service/FavoriteService.java
service/ScanService.java
```

### Fase 2.7 — Controllers

```
controller/TrackController.java
controller/AlbumController.java
controller/ArtistController.java
controller/PlaylistController.java
controller/QueueController.java
controller/HistoryController.java
controller/FavoriteController.java
controller/AdminController.java
```

### Fase 2.8 — Config

- `application.yml`: validar sección `neonvibe.music` (paths, supported-formats,
  scan-interval-seconds).
- `application-dev.yml`: `paths: /tmp/test-music`.

### Fase 2.9 — Tests

```
scanner/MetadataExtractorTest.java
scanner/MusicScannerServiceTest.java
repository/TrackRepositoryTest.java
controller/TrackControllerTest.java
service/PlaylistServiceTest.java
```

---

## 3. Checkpoints de testing tras cada paso mayor

| # | Paso | Verificación |
|---|------|--------------|
| 1 | Migración + entidades | `./mvnw -q compile` |
| 2 | Repositorios | `./mvnw -q compile` |
| 3 | DTOs + mappers | `./mvnw -q compile` (MapStruct genera impls) |
| 4 | Scanner + jaudiotagger | `./mvnw -q compile` |
| 5 | Servicios + controllers | `./mvnw -q compile` |
| 6 | Tests | `./mvnw -q test` |
| 7 | Release | `./mvnw -q clean compile && ./mvnw -q test` |

---

## 4. Riesgos y mitigaciones

### 4.1 Metadatos faltantes / tags vacíos
- **Riesgo:** muchos archivos sin tags (o tags parciales) → datos nulos.
- **Mitigación:** campos de texto quedan `null` (columnas nullable). `title` (NOT NULL)
  usa fallback por nombre de archivo. Parsing de nombre para
  `"Artista - Título"`/`"NN - Título"`. Nunca se lanza por tags vacíos.

### 4.2 Archivo borrado/renombrado durante el escaneo
- **Riesgo:** `NoSuchFileException` a mitad de lectura; evento DELETE duplicado.
- **Mitigación:** try/catch por track en el pipeline. `DELETE`: marcar
  `isAvailable=false` (soft delete), no eliminar fila (preserva favorites/history).
  El soft delete es idempotente. `MODIFY`/`CREATE` re-extraen.

### 4.3 Duplicados / múltiples rutas solapadas
- **Riesgo:** un track ya existe (mismo `filePath`) y se procesa dos veces.
- **Mitigación:** `findByFilePath` + upsert (actualiza si cambió contenido/`updatedAt`
  de mtime). Índice único `uq_track_file_path`. Estructuralmente imposible duplicar path.

### 4.4 WatchService no es recursivo / eventos perdidos
- **Riesgo:** crear directorios anidados no dispara watchers; montajes sin eventos.
- **Mitigación:** escaneo inicial recursivo que registra watchers por directorio; escaneo
  periódico de respaldo (`ScheduledExecutorService`) cuando
  `scan-interval-seconds > 0`; trigger manual `POST /admin/scan`.

### 4.5 Librería de metadatos problemática
- **Riesgo:** jaudiotagger no lee un formato o falla con un archivo concreto.
- **Mitigación:** try/catch por archivo + registro en `ScannerStatus.failedFiles`. Fallback
  por nombre de archivo. (En caso de problema grave con la API, se documenta y se usa un
  parser de tags mínimo o solo filename parsing — ver review.)

### 4.6 MapStruct + Lombok binding
- **Riesgo:** anotación processing no genere impls o choque de anotaciones.
- **Mitigación:** fallback a mappers manuales estáticos (se documenta en la review).

### 4.7 ddl-auto validate vs H2
- **Riesgo:** H2 no acepta tipos PostgreSQL exactos en `create-drop` de tests.
- **Mitigación:** igual que Fase 1: `UserRepositoryTest`/`TrackRepositoryTest` usan
  `@ActiveProfiles("test")` (Flyway off, `ddl-auto: create-drop`, H2 MODE=PostgreSQL).
  Los enums se persisten como VARCHAR para compatibilidad.

---

## 5. Dependencias de artefactos de Fase 1

- **`User` (UUID):** `Playlist`, `PlayQueue`, `PlayHistory`, `Favorite` referencian
  `users.id` (uuid) vía `@ManyToOne` con columna `user_id`.
- **`UserPrincipal` + `JwtAuthenticationFilter`:** los controllers que necesitan el
  usuario leen `principal.id()` del `SecurityContext` (autenticación ya operativa).
- **`ResourceNotFoundException` / `GlobalExceptionHandler`:** se reutilizan para 404 y
  errores estándar.
- **`SecurityConfig`:** `/api/**` ya exige JWT; los nuevos endpoints heredan la
  protección sin cambios.

---

## 6. Notas de despliegue local

- Los tests no requieren `/srv/Music` ni PostgreSQL (H2 + mocks).
- El scanner en dev apunta a `/tmp/test-music` (se crea el directorio si no existe en
  el arranque del watcher, con permisos razonables).
- No se ejecuta escaneo automático de demonio en tests (se desactiva o se inyecta pool
  mocked).
