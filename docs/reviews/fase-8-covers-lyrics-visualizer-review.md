# Fase 8 — Carátulas, Letras y Visualizador: Review

> **Fase:** 8 de 11
> **Base:** `docs/specs/fase-8-covers-lyrics-visualizer-spec.md`, `docs/plans/fase-8-covers-lyrics-visualizer-plan.md`
> **Estado:** ✅ APROBADO (con fixes aplicados tras revisión)

---

## 1. Verificación End-to-End (esta PC)

| Checkpoint | Resultado |
|---|---|
| `./mvnw test` | ✅ 110 tests, 0 fallos (+CoverArtService/LyricsService/CoverController) |
| Flyway V5 + V6 | ✅ Aplicadas (artists.cover_art_path, cover_fetched_at) |
| `pnpm build` (tsc strict) | ✅ Exit 0 |
| Cover álbum fake → placeholder | ✅ SVG determinístico (sin match online) |
| Cover álbum real (Pink Floyd, Queen) | ✅ **JPEG real vía cascade** (iTunes + MusicBrainz) |
| Upload manual cover | ✅ PNG persistido, `cover_art_path` actualizado |
| Cover incrustada (MP3 con artwork) | ✅ extraída al scan, servida |
| Caché | ✅ 2ª request 0.03s (sin red) |
| **TTL anti-tormenta** | ✅ 1ª llamada 2.6s (red), 2ª inmediata |
| Letras LRCLIB (Bohemian Rhapsody) | ✅ `synced=true`, source lrclib, retry sin duration |
| Rutas SPA | ✅ 200 |

## 2. Cambios clave

**Backend**
- Extracción de cover **incrustada** (jaudiotagger `Artwork`) durante el scan → cache `embedded/{trackId}.{ext}`.
- Cascade de covers: DB → cache → incrustada → **iTunes** → **MusicBrainz CAA** → **Last.fm** (opcional) → placeholder SVG determinístico.
- **Calls externas fuera de transacción** (no agotan HikariCP), **TTL** `cover_fetched_at` (7 días) para no re-fetchear álbumes sin cover, **locks por clave** contra cache misses concurrentes.
- Letras LRCLIB con cache fs; retry sin album/duration si el match estricto falla.
- `GET/POST` covers (albums/tracks/artists), `GET /tracks/{id}/lyrics`. `?token=` restringido a `/stream` y `/cover` (media únicamente).
- V5 (artists.cover_art_path) y V6 (cover_fetched_at).

**Frontend**
- `AlbumCover` con `src` real + fallback al gradient neón (`onError`).
- Covers reales en AlbumCard, ArtistCard, PlayerBar, AlbumDetail, ArtistDetail.
- `LyricsSheet`: parse `.lrc`, resalta línea activa por progreso.
- `Visualizer`: grafo Web Audio compartido (`audioGraph.ts`), canvas + rAF, overlay desde PlayerBar; `ensureAudioRunning` evita silencio en play remoto.

## 3. Hallazgos del revisor y correcciones aplicadas

Revisor: **APROBAR CON CAMBIOS**. Corregidos antes del commit:
1. **Red dentro de transacción** → getters sin `@Transactional`; lazy de colecciones reemplazado por `trackRepository.findByAlbumEntityId`.
2. **Tormenta de re-fetch** → TTL `cover_fetched_at` (V6) + los placeholders ya no se cachean.
3. **Límite multipart** → `spring.servlet.multipart.max-*: 10MB` en application.yml.
4. **Race en cache miss** → locks por `albumId`/`artistId` (double-checked).
5. **`?token=` sobre-amplio** → restringido a paths que terminan en `/stream` o `/cover`.
6. **Visualizer puede silenciar audio en sync remoto** → `ensureAudioRunning()` en play.

Menores diferidos (documentados): validación de magic-bytes de upload, `CoverArtStore.find` O(n) → O(1) con extensiones conocidas, `.svg` stale en cache, rename `iTunesClient`→convención, AlbumResponse no expone `cover_fetched_at`.

## 4. Pendientes documentados

- Verificación **visual** en navegador: covers en grids, letras sincronizadas resaltando, visualizador en movimiento.
- Genius fallback (requiere scraping; LRCLIB es primaria).
- `has_lyrics` pre-scan (el flag se marca al primer fetch de letras, no al escanear).
- Arte de MediaSession (reusar cover URL) — pulido.

---

*Review — Fase 8. Fecha: 2026-08-06.*
