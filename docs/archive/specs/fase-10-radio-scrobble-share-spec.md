# Fase 10 — Radio, Scrobbling y Compartir: Especificación

> **Fase:** 10 de 11
> **Tipo:** Backend (radio, Last.fm, settings, public) + Frontend (UI/settings).
> **Dependencias:** Fase 2-9.
> **Objetivo:** Radio por similitud, scrobbling a Last.fm (OAuth + scrobble), compartir playlists públicas sin login, y Settings persistidas en DB.

---

## 1. Alcance

**Radio**
- `GET /radio/seed?track_id=&size=` → tracks similares (mismo género > artista > año/álbum), excluyendo el seed.
- Botones "Radio" en PlayerBar (track actual), AlbumDetail y ArtistDetail.

**Last.fm (scrobbling)**
- OAuth web flow: `auth.gettoken` → redirigir a Last.fm → callback `auth.getsession` → guardar `session_key` en el User.
- Scrobble automático en reproducción significativa (completada o ≥50% o ≥30s), async.
- Config en Settings: conectar/desconectar + toggle scrobbling.
- Configurable por env: `LASTFM_API_KEY` / `LASTFM_API_SECRET`. Si no está configurado → endpoints responden deshabilitado.

**Compartir playlists**
- Endpoint público `GET /api/v1/public/playlists/{id}` (sin auth, permitAll) → detalle si `is_public`, si no 404.
- Ruta frontend `/p/:id` (vista mínima, sin nav) + botón "Compartir" (copia URL) en el detalle.

**Settings (persistidas en DB)**
- Tabla `user_settings` (theme, notifications_enabled, scrobble_enabled, cover_sources).
- `GET/PUT /settings` + `POST /settings/cache/clear`.
- La UI de Settings persiste: tema (DB + localStorage fast-path), notificaciones, scrobbling, sources de carátulas (el backend los respeta), limpieza de cache.
- Estado de Last.fm (conectado/usuario) en el response.

**Fuera de alcance:** transcodificación real (quality selector queda como placeholder no visible — el stream ya entrega el archivo original), notificaciones nativas push (solo la preferencia persistida), radio basada en "vecinos" de audio (solo metadatos).

---

## 2. Contratos Backend

### 2.1 Radio

```
GET /api/v1/radio/seed?track_id=5&size=20
→ 200 Track[]
```
- Scoring: `genre igual +3`, `artist igual +2`, `album igual +1`, `year igual +1` (año exacto). Excluye el seed. Orden por score desc (tiebreak aleatorio), limit size. 404 si el seed no existe.

### 2.2 Last.fm

```
GET /api/v1/lastfm/auth-url          → { url, configured }
GET /api/v1/lastfm/callback?token=   → 302 redirect a /settings?lastfm=connected (o ...error)
POST /api/v1/lastfm/disconnect       → 204
```
- Si no configurado: `auth-url` → `{url: null, configured: false}`.

### 2.3 Public playlist

```
GET /api/v1/public/playlists/{id}    → PlaylistDetail (is_public) | 404
```
- `SecurityConfig`: permitAll `/api/v1/public/**`.

### 2.4 Settings

```
GET /api/v1/settings
→ { theme, notifications_enabled, scrobble_enabled, cover_sources: {iTunes, MusicBrainz, LastFm},
    lastfm: {connected, username} }

PUT /api/v1/settings   (mismos campos, parcial)
→ SettingsResponse actualizado

POST /api/v1/settings/cache/clear     → { cleared: n } (borra covers/lyrics cache)
```

### 2.5 Migración V7

```sql
ALTER TABLE users ADD COLUMN lastfm_username varchar(255);
ALTER TABLE users ADD COLUMN lastfm_session_key varchar(255);
CREATE TABLE user_settings (
  user_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  theme varchar(20) NOT NULL DEFAULT 'dark',
  notifications_enabled boolean NOT NULL DEFAULT true,
  scrobble_enabled boolean NOT NULL DEFAULT true,
  cover_sources jsonb NOT NULL DEFAULT '{"iTunes":true,"MusicBrainz":true,"LastFm":true}',
  updated_at timestamptz NOT NULL DEFAULT now()
);
```

---

## 3. Arquitectura Backend

```
dto/SettingsResponse.java
dto/SettingsRequest.java
dto/RadioSeedRequest (no — query params)
domain/UserSettings.java
service/RadioService.java
service/UserSettingsService.java
service/LastFmAuthService.java
service/LastFmScrobbler.java
controller/RadioController.java
controller/LastFmController.java
controller/PublicController.java
controller/SettingsController.java
infra/LastFmClient.java (se extiende: signing + methods auth/scrobble)
resources/db/migration/V7__lastfm_and_settings.sql
config/AsyncConfig.java (@EnableAsync)
security/SecurityConfig.java (+public permitAll)
service/PlayHistoryService.java (+hook scrobble)
```

### 3.1 Radio (`RadioService`)
- `radioForSeed(trackId, size)`:
  1. seed = trackRepository.findById → 404 si falta.
  2. tracks = trackRepository.findAllByIsAvailableTrue() (nueva query List).
  3. score in-memory vs seed; excluir seed; orden score desc + `random()`;
  4. limit size → TrackResponse[].

### 3.2 Last.fm (`LastFmAuthService` + `LastFmScrobbler`)
- Config: `neonvibe.lastfm.api-key`, `neonvibe.lastfm.api-secret` (env). `configured()`.
- MD5 signing (`api_sig`) para todos los métodos.
- `getAuthUrl(userId)`: `auth.gettoken` → guarda `pendingTokens[token] = userId` (TTL 15 min, ConcurrentHashMap) → URL de auth.
- `handleCallback(token)`: `auth.getsession` → sessionKey/username → guarda en User.
- `Scrobbler.scrobble(userId, artist, track, album, duration, timestamp)` `@Async`: POST `track.scrobble` firmado; loguea éxito/fallo, no lanza.
- **Hook**: `PlayHistoryService.record`/`recordIfSignificant` → `maybeScrobble` si: user.lastfmSessionKey != null && settings.scrobbleEnabled && (completed || listened >= min(30, duration/2)).

### 3.3 Public playlist
- `PlaylistService.getPublic(id)` → detalle si `isPublic`, si no 404 (sin contexto de usuario).
- `PublicController` en `/api/v1/public/playlists/{id}`.

### 3.4 Settings (`UserSettingsService`)
- `getForUser(userId)` → crea default si no existe.
- `update(userId, request)` → merge parcial.
- `clearCache()` → borra `./data/covers/*` y `./data/lyrics/*`, devuelve nº de archivos.
- CoverArtService lee `cover_sources` del usuario actual (fallback: todos habilitados si no hay usuario) para saltar fuentes deshabilitadas.

---

## 4. Arquitectura Frontend

```
api/radio.ts        # getRadioSeed(trackId, size)
api/settings.ts     # getSettings/updateSettings/clearCache/getLastFmAuthUrl/disconnectLastFm
hooks/useSettings.ts
pages/SettingsPage.tsx  (reescrita: cuenta, tema→DB, notificaciones, scrobbling, Last.fm, cover sources, cache)
pages/PublicPlaylistPage.tsx (/p/:id)
components/RadioButton.tsx  (dispara radio desde track/álbum/artista)
components/PlayerBar.tsx    (+botón Radio del track actual)
pages/AlbumDetailPage.tsx, ArtistDetailPage.tsx (+Radio)
pages/PlaylistDetailPage.tsx (+Compartir)
App.tsx (ruta /p/:id sin Layout)
```

### 4.1 Radio en UI
- `RadioButton` (props: trackId, label?) → `getRadioSeed(trackId)` → `playerStore.playTrack(primer track, radioQueue)`.
- PlayerBar: icono Radio (usa currentTrack.id).
- AlbumDetail/ArtistDetail: botón "Radio" en el header (seed = primera canción del álbum/artista).

### 4.2 Compartir
- `PlaylistDetailPage`: botón "Compartir" (solo públicas) → `navigator.clipboard.writeText(origin + '/p/' + id)`.
- `PublicPlaylistPage` (`/p/:id`): layout mínimo (header con logo + back), usa `GET /api/v1/public/playlists/{id}` (no necesita auth); tracks listados con play habilitado solo si hay sesión.

### 4.3 Settings
- Carga `GET /settings` tras auth; aplica theme al store; los toggles hacen `PUT /settings` (debounced para theme).
- Last.fm: si `!configured` → texto "Configura LASTFM_API_KEY/SECRET en el servidor"; si conectado → usuario + "Desconectar"; si no → botón "Conectar con Last.fm" (abre auth-url).
- Cache: botón "Limpiar cache de carátulas/letras".
- Cover sources: 3 toggles.

---

## 5. Criterios de Aceptación

1. Radio devuelve ≥20 tracks coherentes (mismo género/artista/año) y la UI los reproduce como cola.
2. Scrobbling: con API key real, completar un track lo registra en Last.fm. Sin key → endpoints deshabilitados con mensaje claro.
3. Playlist pública visible sin login en `/p/:id`; privada → 404.
4. Settings persisten en DB (recargar conserva theme, notificaciones, scrobble, cover sources).
5. `./mvnw test` y `pnpm build` verdes.

---

## 6. Riesgos y Mitigaciones

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Sin API keys de Last.fm en dev | Scrobble no testeable real | Código completo + tests de signing/URL; validación manual con keys reales (pendiente) |
| MD5 signing mal implementado | 401 de Last.fm | Test unitario de `api_sig` contra ejemplo conocido |
| `api_sig` usa parámetros ordenados | Firma inválida | Ordenar params alfabéticamente antes de firmar |
| Public endpoint filtra `file_path` | Leak menor | Reusar PlaylistDetailResponse; documentado (self-hosted) |
| Radio O(n) en memoria | 4307 tracks OK | In-memory; si crece, mover a SQL |

---

*Documento de especificación — Fase 10. Fecha: 2026-08-06.*
