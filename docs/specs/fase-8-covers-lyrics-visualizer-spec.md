# Fase 8 — Carátulas, Letras y Visualizador: Especificación

> **Fase:** 8 de 11
> **Tipo:** Backend (servicios online/cache) + Frontend (visual).
> **Dependencias:** Fase 2 (entidades), Fase 5 (UI/library), Fase 7 (reproductor + audio singleton).
> **Objetivo:** Carátulas reales (incrustadas + online + manual), letras (LRCLIB con sync) y visualizador de audio neón.

---

## 1. Alcance

**Backend**
- Extracción de carátula **incrustada** (ID3/Vorbis/MP4) durante el escaneo → cache a filesystem.
- Servicio de carátulas con **cascade online**: iTunes → MusicBrainz CAA → Last.fm (opcional, sin key → se salta) → placeholder SVG determinístico.
- Cache en filesystem (`neonvibe.covers.cache-path`); no se re-descarga en cada scan/request.
- Endpoints: `GET/albums/{id}/cover`, `GET/tracks/{id}/cover`, `GET/artists/{id}/cover`, `POST/albums/{id}/cover` (manual multipart), `POST/artists/{id}/cover`.
- Letras: **LRCLIB** primaria (sincronizada `.lrc` o plano), cache en filesystem, `GET/tracks/{id}/lyrics`. Genius como fallback queda pendiente (requiere scraping, no API pública).

**Frontend**
- Carátulas reales (vía `<img>` con `?token=`) en `AlbumCard`, `ArtistCard`, `PlayerBar`, `AlbumDetail`, `ArtistDetail`, `PlaylistCard`; `AlbumCover` acepta `src` con fallback al gradient neón.
- Vista de **letras** (bottom sheet) con resaltado de línea sincronizada si `.lrc`.
- **Visualizador**: `AnalyserNode` + Canvas 2D (barras neón), overlay desde `PlayerBar`, fallback si no hay soporte/gesto de usuario.

**Fuera de alcance:** Genius fallback, descarga de letras/covers offline (Fase 9), radio/scrobbling (Fase 10), covers para playlists online.

---

## 2. Contratos Backend

### 2.1 Covers

| Método + Ruta | Body | Respuesta |
|---|---|---|
| `GET /albums/{id}/cover` | — | imagen (jpeg/png/svg) `200`; `404` si el álbum no existe |
| `GET /tracks/{id}/cover` | — | imagen (cover incrustado del track o del álbum) |
| `GET /artists/{id}/cover` | — | imagen (Last.fm o placeholder) |
| `POST /albums/{id}/cover` | `multipart/form-data` field `file` | `Album` actualizado (`cover_art_path`) |
| `POST /artists/{id}/cover` | `multipart/form-data` field `file` | `Artist` actualizado |

- Autenticación: `/api/**` (Bearer). Para `<img>`/MediaSession el token viaja en query param; el filtro JWT ya acepta `?token=` para `/api/v1/tracks/` y `/api/v1/albums/` → se añade `/api/v1/artists/`.
- Orden de resolución (álbum): `cover_art_path` en DB → cache `album/{id}.*` → incrustada de un track del álbum → iTunes → MusicBrainz → Last.fm → placeholder SVG.
- El placeholder es SVG determinístico (gradiente por hash de nombre) para que las URLs nunca devuelvan error de imagen.

### 2.2 Letras

| Método + Ruta | Respuesta |
|---|---|
| `GET /tracks/{id}/lyrics` | `LyricsResponse` |

```json
{ "track_id": 1, "synced": true, "lyrics": "[00:12.00]Neon heart...", "source": "lrclib" }
```
- `lyrics` y `synced` son `null/false` cuando no hay letras (HTTP 200, sin error).
- Cache: `{lyricsCache}/{trackId}.lrc` (si synced) o `.txt`. Al encontrar letras se marca `track.has_lyrics = true`.

### 2.3 Config

```yaml
neonvibe:
  covers:
    cache-path: ./data/covers      # ya existente
  lyrics:
    cache-path: ./data/lyrics      # nuevo
  lastfm:
    api-key: ${LASTFM_API_KEY:}    # opcional; si vacío se salta Last.fm
```

---

## 3. Arquitectura Backend

```
infra/
  CoverArtStore.java        # filesystem: find/read/write por categoría (album/artist/embedded/lyrics)
  iTunesClient.java         # search → artwork bytes
  MusicBrainzClient.java    # release-group mbid → CAA bytes (User-Agent)
  LastFmClient.java         # album/artist getinfo → image (solo con api-key)
  LrclibClient.java         # get → synced/plain lyrics
service/
  CoverArtService.java      # cascade + placeholder SVG + manual + cache/DB
  LyricsService.java        # get/parse/cache lyrics
controller/
  CoverController.java
  LyricsController.java
dto/
  LyricsResponse.java
config/
  HttpClientConfig.java     # RestClient externo con timeouts
scanner/
  EmbeddedArt.java          # record (data, extension)
  MetadataExtractor.java    # + default extractEmbedded(Path)
  JAudioTaggerMetadataExtractor.java  # implementa extractEmbedded
  LibrarySyncService.java   # guarda cover embebido tras guardar el track
```

### 3.1 `CoverArtStore`
- Base dir desde config (se crea al arrancar).
- API: `Path fileFor(String category, long id, String ext)`, `Optional<Path> find(String category, long id)` (busca por extensiones conocidas: jpg/png/webp/svg/lrc/txt), `byte[] read(Path)`, `void write(Path, byte[])`.

### 3.2 Cascade en `CoverArtService.getAlbumCoverBytes(albumId)`
1. `album.coverArtPath` (manual o previa) → leer.
2. cache `album/{id}.*` → devolver.
3. track incrustado: primer track disponible con `coverArtPath` → copiar a cache de álbum + set `album.coverArtPath`.
4. iTunes search (term = `"{album}" {artist}`) → `artworkUrl100` → ampliar a 600x600.
5. MusicBrainz release-group search → CAA `front-500`.
6. Last.fm `album.getinfo` (si key).
7. placeholder SVG.
- Cada acierto online se guarda en cache + `album.coverArtPath` (no re-descarga).

### 3.3 Placeholder SVG
- Gradiente determinístico por hash(`albumId`+nombre) sobre paleta neón. `image/svg+xml`.

### 3.4 Lyrics (`LyricsService`)
- Cache `lyrics/{trackId}.lrc|txt` → devolver.
- `LrclibClient.fetch(artist, title, album, duration)` → `syncedLyrics ?? plainLyrics`.
- Marcar `has_lyrics`, persistir, cachear, devolver `LyricsResponse`.

---

## 4. Arquitectura Frontend

```
api/cover.ts     # albumCoverUrl/trackCoverUrl/artistCoverUrl (con ?token=) + uploadCover
api/lyrics.ts    # getLyrics(trackId)
components/
  AlbumCover.tsx # + prop src: render <img>, onError → gradient
  LyricsSheet.tsx
  Visualizer.tsx
  PlayerBar.tsx  # botones letras (FileText) + visualizer (Waves); mini cover real
```

### 4.1 Covers en componentes
- `AlbumCard`/`ArtistCard`/`AlbumDetailPage`/`ArtistDetailPage`: `src={albumCoverUrl(id)}` / `artistCoverUrl(id)`.
- `PlayerBar`: `trackCoverUrl(currentTrack.id)`.
- `PlaylistCard`: usa la cover del primer track? No hay endpoint → se mantiene gradient (anotado).
- `AlbumCover`: si `src` → `<img loading="lazy" onError={() => setFailed(true)}>`; si falla o no hay `src` → gradient.

### 4.2 `LyricsSheet`
- Bottom sheet con las letras del `currentTrack`.
- `useQuery(['lyrics', trackId])`.
- Si `synced`: parsear `.lrc` (`[mm:ss.xx] texto`) y resaltar la línea activa según `playerStore.progress`.
- Si plano: scroll simple. Si `lyrics == null`: "Sin letras disponibles".

### 4.3 `Visualizer`
- Web Audio: `AudioContext` perezoso (requiere gesto de usuario; se crea en el primer play).
- Grafo único por sesión: `createMediaElementSource(audio)` → `analyser` → `destination` (guard a nivel módulo).
- `Visualizer` lee `analyser.getByteFrequencyData` con `requestAnimationFrame` y dibuja barras neón en canvas.
- Overlay fullscreen desde `PlayerBar` (icono Waves). Fallback: mensaje si `AudioContext` no disponible o `state === 'suspended'`.
- `playerStore` expone `getAudioElement()`.

---

## 5. Criterios de Aceptación

1. Álbumes sin carátula incrustada obtienen imagen online automáticamente (o placeholder si no se encuentra).
2. La imagen se cachea y no se re-descarga (verificable: segunda request a cache, `album.coverArtPath` poblado).
3. Covers incrustadas se extraen al escanear.
4. `GET /tracks/{id}/lyrics` devuelve letras de LRCLIB (o null) y marca `has_lyrics`.
5. Letras sincronizadas resaltan la línea actual en el reproductor.
6. Visualizador responde al audio en tiempo real (30fps+) y no silencia el audio.
7. Upload manual persiste y reemplaza la automática.
8. `./mvnw test` y `pnpm build` verdes.

---

## 6. Riesgos y Mitigaciones

| Riesgo | Impacto | Mitigación |
|---|---|---|
| APIs online inestables/lentas | Requests lentos | Timeouts cortos (6s) en RestClient externo + fallback cascade |
| Rate limit MusicBrainz (1 rps) | 429 | iTunes primero; MB solo si iTunes falla |
| Fake seed sin match online | Sin carátula en dev | Placeholder SVG (test de cascade con álbum real en review) |
| `createMediaElementSource` silencia audio si no se conecta | Audio mudo | Grafo único + siempre conectado a destination |
| Autoplay/AudioContext suspendido | Visualizer vacío | Crear en gesto de usuario; fallback visual |
| `<img>` con token en URLs | Cache/logs | Aceptado para MVP (mismo criterio que stream); nota en pendientes |

---

*Documento de especificación — Fase 8. Fecha: 2026-08-06.*
