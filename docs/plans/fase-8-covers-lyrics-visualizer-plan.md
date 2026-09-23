# Fase 8 — Carátulas, Letras y Visualizador: Plan de Implementación

> **Fase:** 8 de 11
> **Base:** `docs/specs/fase-8-covers-lyrics-visualizer-spec.md`
> **Estado:** Backend (servicios online/cache) + Frontend (visual).

---

## 1. Orden de Pasos

### Backend
1. `HttpClientConfig` (RestClient externo con timeouts).
2. `EmbeddedArt` + `extractEmbedded` en `MetadataExtractor`/`JAudioTaggerMetadataExtractor`.
3. `CoverArtStore` (filesystem cache).
4. `LibrarySyncService`: guarda cover embebido al hacer upsert.
5. Clientes: `iTunesClient`, `MusicBrainzClient`, `LastFmClient`, `LrclibClient`.
6. `CoverArtService` (cascade + placeholder SVG + manual) y `LyricsService`.
7. `CoverController` + `LyricsController`; `JwtAuthenticationFilter` añade `/api/v1/artists/` a `?token=`.
8. Tests.

### Frontend
9. `api/cover.ts`, `api/lyrics.ts`; `AlbumCover` con `src` + fallback.
10. Covers reales en cards/PlayerBar/detalles.
11. `LyricsSheet` + botón en `PlayerBar`.
12. `Visualizer` (Web Audio + Canvas) + overlay.
13. Checkpoints.

---

## 2. Secuencia de Archivos

### Backend
```
config/HttpClientConfig.java
scanner/EmbeddedArt.java
scanner/MetadataExtractor.java          (+default extractEmbedded)
scanner/JAudioTaggerMetadataExtractor.java
infra/CoverArtStore.java
infra/iTunesClient.java
infra/MusicBrainzClient.java
infra/LastFmClient.java
infra/LrclibClient.java
service/CoverArtService.java
service/LyricsService.java
controller/CoverController.java
controller/LyricsController.java
dto/LyricsResponse.java
security/JwtAuthenticationFilter.java    (+artists)
scanner/LibrarySyncService.java          (+cover embebido)
resources/application.yml                (+lyrics cache, lastfm key)
test/... (CoverArtServiceTest, LyricsServiceTest, CoverControllerTest)
```

### Frontend
```
api/cover.ts
api/lyrics.ts
components/AlbumCover.tsx    (src + onError fallback)
components/AlbumCard.tsx     (src real)
components/ArtistCard.tsx    (src real)
components/PlaylistCard.tsx  (gradient; nota)
components/PlayerBar.tsx     (mini cover real + botones letras/visualizer)
components/LyricsSheet.tsx
components/Visualizer.tsx
pages/AlbumDetailPage.tsx, pages/ArtistDetailPage.tsx (src real)
stores/playerStore.ts        (+getAudioElement, currentTrack cover)
```

---

## 3. Checkpoints de Prueba

| # | Checkpoint | Cómo verificar |
|---|---|---|
| C1 | `./mvnw test` | Exit 0 |
| C2 | `pnpm build` | Exit 0 |
| C3 | Cover endpoint devuelve imagen (placeholder para seed fake; real para álbum conocido) | curl `albums/1/cover` |
| C4 | Cover se cachea (2ª request inmediata; `album.coverArtPath` poblado) | curl + GET /albums/1 |
| C5 | Covers incrustadas se extraen (MP3 con artwork) | curl tras rescan |
| C6 | `GET /tracks/{id}/lyrics` real (LRCLIB) o null | curl |
| C7 | Upload manual reemplaza carátula | curl `-F file=@...` + GET cover |
| C8 | Visualizer dibuja sin silenciar audio | navegador |
| C9 | Letras sincronizadas resaltan línea | navegador |

---

## 4. Riesgos y Contingencias

| Riesgo | Señal | Plan B |
|---|---|---|
| iTunes/MB devuelven 429/error | cover null | Siguiente cliente del cascade → placeholder |
| LRCLIB 404 | lyrics null | Devolver 200 con lyrics null |
| AudioContext suspende | visualizer vacío | Mensaje + reanudar en gesto |
| jaudiotagger Artwork nulo | cover embebida null | Continuar con cascade online |

---

## 5. Reglas de Código

- Servicios online con timeouts cortos y `@Component`; testables por inyección.
- Cache en filesystem, nunca en memoria (evita re-descargas).
- Placeholder SVG determinístico; el frontend conserva su fallback gradient.
- El audio sigue siendo el singleton del `playerStore`; el visualizer no desconecta el grafo (evita silencio).
- Commits convencionales en inglés.

---

*Plan de implementación — Fase 8. Fecha: 2026-08-06.*
