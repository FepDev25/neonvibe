# Fase 10 — Radio, Scrobbling y Compartir: Review

> **Fase:** 10 de 11
> **Base:** `docs/specs/fase-10-radio-scrobble-share-spec.md`, `docs/plans/fase-10-radio-scrobble-share-plan.md`
> **Estado:** ✅ APROBADO (con fixes aplicados tras revisión)

---

## 1. Verificación (esta PC)

| Checkpoint | Resultado |
|---|---|
| `./mvnw test` | ✅ 116 tests (+RadioService, LastFmClient signing) |
| Flyway V7 | ✅ lastfm cols + user_settings |
| Radio seed (Synthwave) | ✅ 5+ tracks del mismo género, seed excluido |
| Settings GET/PUT | ✅ persisten (light/notif/scrobble); defaults creados en GET |
| `POST /settings/cache/clear` | ✅ `{"cleared": 5}` |
| Playlist pública sin token | ✅ 200 (DTO sin `file_path`); privada → 404 |
| Last.fm callback sin token | ✅ 302 (permitAll) |
| Last.fm auth-url sin keys | ✅ `configured: false` |
| `pnpm build` (tsc strict) | ✅ Exit 0 |

*(Scrobble real requiere API keys de Last.fm — validación manual pendiente.)*

## 2. Cambios clave

**Backend**
- Radio por similitud (scoring genre/artist/album/year, excluye seed, tiebreak con shuffle + stable sort).
- Last.fm OAuth: `auth.gettoken` → URL → callback `auth.getsession` (pendingTokens con TTL), guardado en User; scrobble `@Async` con MD5 `api_sig`; hook en PlayHistoryService con dedupe de scrobble doble.
- Playlist pública: `GET /api/v1/public/playlists/{id}` permitAll con **DTO recortado** (`PublicPlaylistResponse`/`PublicTrackResponse`, sin `file_path`/`cover_art_path`).
- Settings persistidas en DB: `user_settings` (V7), `GET/PUT /settings`, `POST /settings/cache/clear`; CoverArtService respeta `cover_sources`.
- `@EnableAsync` (AsyncConfig).

**Frontend**
- `RadioButton` + radio en PlayerBar, AlbumDetail, ArtistDetail (reproduce la cola de radio).
- Settings reescrita (cuenta, tema→DB, notificaciones, Last.fm conectar/desconectar + scrobble toggle, fuentes de carátulas, limpiar cache).
- `PublicPlaylistPage` (`/p/:id`) standalone sin login + botón "Compartir" (copia URL) en playlists públicas.

## 3. Hallazgos del revisor y correcciones aplicadas

Revisor: **APROBAR CON CAMBIOS**. Corregidos antes del commit:
1. **Callback de Last.fm devolvía 401** (no estaba en permitAll) → añadido `/api/v1/lastfm/callback`.
2. **Defaults de settings perdidos en GET** (`readOnly` intentaba guardar) → `getForUser` sin tx de lectura; el `save` de defaults abre su propia tx.
3. **Comparator no transitivo (Math.random)** → shuffle previo + stable sort por score.
4. **Scrobble doble** → guard de historial reciente completado (`existsByUserIdAndTrackIdAndCompletedTrueAndPlayedAtAfter` 30s).
5. **DTO público filtraba `file_path`/`cover_art_path`** → nuevos `PublicPlaylistResponse`/`PublicTrackResponse`.

## 4. Pendientes documentados

- **Validación con API keys reales de Last.fm**: configurar `LASTFM_API_KEY`/`LASTFM_API_SECRET`, conectar desde Settings, completar un track y verificar el scrobble en el perfil.
- `pendingTokens` en memoria sin barrido periódico (single-instance OK; cluster futuro → Caffeine/Redis).
- Scrobble de "now playing" (updateNowPlaying) no implementado — solo scrobble al completar.

---

*Review — Fase 10. Fecha: 2026-08-06.*
