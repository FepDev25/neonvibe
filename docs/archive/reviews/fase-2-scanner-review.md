# Fase 2 — Dominio Musical y Scanner: Revisión

> Fecha: 2026-08-06
> Revisado por: Subagente Fase 2 (entorno sandbox)
> Base: spec (`docs/specs/fase-2-scanner-spec.md`) y plan (`docs/plans/fase-2-scanner-plan.md`)

---

## 1. Resumen

Se implementó el dominio musical de NeonVibe (Fase 2): las 8 entidades JPA
(`Track`, `Album`, `Artist`, `Playlist`, `PlaylistTrack`, `PlayQueue`, `PlayHistory`,
`Favorite`), sus repositorios y la migración Flyway `V3__music_schema.sql`. Se añadió un
scanner de biblioteca (WatchService + escaneo inicial + poll periódico opcional) con
pipeline de extracción de metadatos vía jaudiotagger 3.0.1 y sync a la base de datos.
Se expusieron los endpoints REST (tracks, álbumes, artistas, playlists, queue, history,
favorites, admin/scan), todo con DTOs (nunca entidades JPA), constructor injection y
los patrones de Fase 0/1 (error JSON estándar, snake_case, excepción global).

## 2. Resultados de compilación y tests

| Prueba | Resultado |
|--------|-----------|
| `./mvnw clean compile` | **PASS** (0 errores) |
| `./mvnw clean test` | **PASS** (54/54, 0 failures, 0 errors) |

### Detalle de tests (54 total)

| Clase de test | Tests | Resultado |
|---------------|-------|-----------|
| `scanner/MetadataExtractorTest` (nuevo) | 5 | PASS |
| `scanner/MusicScannerServiceTest` (nuevo) | 5 | PASS |
| `repository/TrackRepositoryTest` (nuevo) | 5 | PASS |
| `controller/TrackControllerTest` (nuevo) | 5 | PASS |
| `service/PlaylistServiceTest` (nuevo) | 5 | PASS |
| `config/SecurityConfigTest` (fase 1) | 3 | PASS |
| `controller/AuthControllerTest` (fase 1) | 6 | PASS |
| `exception/GlobalExceptionHandlerTest` (fase 1) | 4 | PASS |
| `NeonVibeApplicationTests` (context load) | 1 | PASS |
| `repository/UserRepositoryTest` (fase 1) | 4 | PASS |
| `security/JwtTokenProviderTest` (fase 1) | 6 | PASS |
| `service/AuthServiceTest` (fase 1) | 5 | PASS |

Se añadieron **5 clases de test nuevas** (25 tests nuevos), cumpliendo el mínimo de 5.

## 3. Checklist de criterios de aceptación

| Ítem | Estado |
|------|--------|
| Entidades JPA con relaciones/cascadas/índices; `ddl-auto: validate` consistente | ✅ PASS — migración `V3` alineada con entidades (validado en @SpringBootTest) |
| Scanner async (no bloquea requests) + WatchService + escaneo inicial + poll opcional | ✅ PASS — `ScannerExecutorConfig` (pool acotado), `FileWatcherService`, `MusicScannerService` |
| `GET /tracks` paginación + filtros (q/artist/album/genre/year) | ✅ PASS — `TrackController` + `TrackRepository.search` (JPQL condicional) |
| Endpoints queue/history/favorites/playlists filtran por `user_id` del JWT | ✅ PASS — `SecurityUtils.currentUser()` + `principal.id()` |
| Solo DTOs en controllers; constructor injection | ✅ PASS — 0 `@Autowired` en campos (main); controllers no importan entidades JPA (solo el enum `FavoriteEntityType`) |
| Errores de scanner por archivo no tumban la app | ✅ PASS — try/catch por track + `ScannerStatus.failedFiles` |
| Metadatos MP3 (title, artist, album, year, genre, track_number) | ✅ PASS — jaudiotagger (ID3/Vorbis) con fallback por filename |
| Archivo borrado → soft delete (`is_available=false`) | ✅ PASS — `LibrarySyncService.markUnavailable` idempotente |
| Errores scanner logueados, fallidos rastreados | ✅ PASS — `incFailed` + map path→reason |

## 4. Cobertura de código (resumen cualitativo)

- **Scanner pipeline** (MusicScannerServiceTest + MetadataExtractorTest): ramas de
  archivo soportado/no soportado, persistencia, fallo por archivo, borrado lógico y
  parsing de filename (artista-título, número-título, 3 segmentos, nombre plano).
- **Repositorios** (TrackRepositoryTest @DataJpaTest sobre H2 MODE=PostgreSQL): `search`
  por artista, género+año, q (title, ignore case), `findByFilePath` y unicidad del path.
- **Controllers** (TrackControllerTest @WebMvcTest con `SecurityConfig` + JWT real):
  listado paginado, filtros, detalle, 404, 401 sin token.
- **Servicios** (PlaylistServiceTest): create, ownership (403/401 en update de tercero),
  delete missing, append track al final, reorder con realineación.

No se midió % de cobertura con JaCoCo (no configurado en el proyecto); la cobertura es
funcional/manual sobre las ramas clave. Se recomienda añadir JaCoCo en una fase posterior.

## 5. Decisiones y deviaciones respecto al spec/plan

1. **Librería de metadatos:** se eligió e implementó **jaudiotagger 3.0.1** (como se
   especificó). Funciona sin problemas con Java 21 / Spring Boot 3.4.13; tolera archivos
   corruptos/ilegibles devolviendo un `MusicMetadata` mínimo (title desde filename).
2. **`GET /tracks` sin filtros:** en lugar de `findAll`, se usa
   `findAllByIsAvailableTrue` para no listar tracks borrados (soft-deleted).
3. **`AlbumResponse.trackCount`:** MapStruct ignora el campo (`@Mapping(ignore=true)`)
   y el servicio rellena el conteo derivándolo de la colección lazy (sin cargar todo el
   grafo en listados). Documentado en `AlbumMapper`.
4. **Manejo de enum `year` en H2:** `track.year` es palabra reservada en H2. Se habilitó
   `spring.jpa.properties.hibernate.auto_quote_keyword=true` en los profiles de test para
   que Hibernate cuote la columna (`"year"`); PostgreSQL la acepta sin comillas, por lo
   que la migración `V3` no cambió.
5. **Circularidad de beans rota:** `FileWatcherService` ya no inyecta
   `MusicScannerService`; expone un `setEventHandler(BiConsumer)` que el orquestador
   cablea en `@PostConstruct`, evitando `allow-circular-references`.
6. **Test del scanner sin FS real:** `isSupportedFile` exige `Files.isRegularFile`, así
   que `MusicScannerServiceTest` crea archivos reales en un `@TempDir`.
7. **`ScanStatusResponse`:** factoría estática `fromState(...)` para desacoplar el DTO
   del `ScannerStatus` interno (evita acoplar capas).
8. **`FavoriteController` importa `com.neonvibe.domain.FavoriteEntityType`** — es un
   enum (tipo de valor) usado como param de query, no una entidad JPA expuesta; cumple
   la regla de "no exponer entidades".
9. **Restricción `(playlist_id, position)`:** el reorder en una misma transacción durante
   un flush con posiciones intermedias duplicadas podría chocar con el unique constraint
   en PostgreSQL (H2 lo tolera). Para el MVP es aceptable; en iteración futura se puede
   reindexar con un batch UPDATE intermedio (o `REORDER BY` en dos pasos).

## 6. Bloqueantes / no verificable

- **Integración real con PostgreSQL** (igual que fases anteriores): no se pudo lanzar
  Docker. La migración `V3` usa tipos válidos de PostgreSQL (`uuid`, `bigserial`,
  `timestamptz`, FK a `users.id`), pero debe confirmarse con `docker compose up` +
  profile dev.
- **Escaneo real de `/srv/Music`:** en el sandbox el profile test apunta a
  `/nonexistent-test-music` para que el arranque de tests sea rápido y no toque la
  biblioteca real. La lógica del scanner (caminar árbol, extraer, upsert, soft-delete) se
  validó unitariamente; una verificación end-to-end contra un directorio con música real
  requiere el servidor con permisos (`felipep:felipep`).
- **Metadatos reales MP3/FLAC con tags:** `MetadataExtractorTest` cubre el fallback por
  filename y el manejo de archivos ilegibles; no hay archivos MP3/FLAC reales con tags
  en el sandbox para probar la lectura real de ID3/Vorbis.

## 7. Notas para la Fase 3 (streaming)

- `Track` expone `filePath`, `format` y `mimeType`; `GET /tracks/{id}/stream` (Fase 3)
  podrá resolver el archivo desde `filePath` y servir con `ResourceRegion` para HTTP
  Range requests. `mimeTypeFor` en `JAudioTaggerMetadataExtractor` ya puebla el tipo
  MIME correcto por extensión.
- La entidad `PlayQueue` ya separa `currentTrackId`, `positionSeconds`, `shuffleEnabled`,
  `repeatMode` (enum `NONE/ALL/ONE`) y `tracksOrder` (JSON string) — el reproductor
  (Fase 3/7) consume `GET/PUT /api/v1/queue` directamente.
- `PlayHistory` con `completed` y `durationListenedSeconds` está listo para que el
  frontend registre scrobbling al terminar tracks.
- El scanner queda como fondo asíncrono; Fase 3 puede publicar eventos de progreso por
  WebSocket (`SCANNER_PROGRESS` / `NEW_TRACKS`) leyendo `ScannerStatus` / `failedFiles`.
