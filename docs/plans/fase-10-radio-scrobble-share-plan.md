# Fase 10 — Radio, Scrobbling y Compartir: Plan de Implementación

> **Fase:** 10 de 11
> **Base:** `docs/specs/fase-10-radio-scrobble-share-spec.md`
> **Estado:** Backend (radio, Last.fm, settings, public) + Frontend.

---

## 1. Orden de Pasos

### Backend
1. V7 migration + entidades (`User.lastfm*`, `UserSettings`).
2. `AsyncConfig` (@EnableAsync).
3. `RadioService` + `RadioController` + query `findAllByIsAvailableTrue()`.
4. `UserSettingsService` + DTOs + `SettingsController` (+ cache clear).
5. `LastFmAuthService` + `LastFmScrobbler` (extender `LastFmClient` con signing) + `LastFmController`.
6. Hook de scrobble en `PlayHistoryService`.
7. `PlaylistService.getPublic` + `PublicController` + `SecurityConfig` permitAll.
8. CoverArtService respeta `cover_sources`.
9. Tests.

### Frontend
10. `api/radio.ts`, `api/settings.ts`, `hooks/useSettings.ts`.
11. `SettingsPage` reescrita.
12. `RadioButton` + integración (PlayerBar, AlbumDetail, ArtistDetail).
13. `PublicPlaylistPage` (`/p/:id`) + botón Compartir en PlaylistDetail.
14. Checkpoints.

---

## 2. Secuencia de Archivos

### Backend
```
db/migration/V7__lastfm_and_settings.sql
domain/User.java (+lastfmUsername, lastfmSessionKey)
domain/UserSettings.java
dto/SettingsResponse.java, dto/SettingsRequest.java
config/AsyncConfig.java
repository/UserSettingsRepository.java
repository/TrackRepository.java (+findAllByIsAvailableTrue List)
service/RadioService.java
service/UserSettingsService.java
service/LastFmAuthService.java
service/LastFmScrobbler.java
infra/LastFmClient.java (extensión signing)
service/PlayHistoryService.java (+maybeScrobble)
service/CoverArtService.java (+cover sources)
service/PlaylistService.java (+getPublic)
controller/RadioController.java
controller/LastFmController.java
controller/SettingsController.java
controller/PublicController.java
config/SecurityConfig.java (+/api/v1/public/**)
test/service/RadioServiceTest.java
test/service/LastFmAuthServiceTest.java (signing/URL)
test/controller/SettingsControllerTest.java
```

### Frontend
```
api/radio.ts
api/settings.ts
hooks/useSettings.ts
pages/SettingsPage.tsx (reescrita)
pages/PublicPlaylistPage.tsx
components/RadioButton.tsx
components/PlayerBar.tsx (+Radio)
components/PlaylistDetailPage.tsx (+Compartir)
pages/AlbumDetailPage.tsx, ArtistDetailPage.tsx (+Radio)
App.tsx (ruta /p/:id)
```

---

## 3. Checkpoints de Prueba

| # | Checkpoint | Cómo verificar |
|---|---|---|
| C1 | `./mvnw test` | Exit 0 |
| C2 | V7 aplica (lastfm cols + user_settings) | Flyway |
| C3 | `GET /radio/seed?track_id=` devuelve tracks coherentes | curl |
| C4 | `GET /settings` + `PUT` persisten (reload) | curl |
| C5 | `POST /settings/cache/clear` borra cache | curl |
| C6 | `GET /api/v1/public/playlists/{id}` pública 200 / privada 404, sin token | curl |
| C7 | `GET /lastfm/auth-url` → configured:false sin keys | curl |
| C8 | `pnpm build` (tsc strict) | Exit 0 |
| C9 | Radio reproduce cola en navegador | manual |
| C10 | `/p/:id` vista pública sin login | manual |

---

## 4. Riesgos y Contingencias

| Riesgo | Señal | Plan B |
|---|---|---|
| Last.fm sin keys | auth-url configured:false | UI muestra configuración pendiente |
| MD5 sig mal | 401 | Test con ejemplo conocido + logs |
| `cover_sources` sin usuario | NPE | Fallback all-enabled |
| Public DTO filtra paths | — | Aceptado MVP; documentado |

---

## 5. Reglas de Código

- Scrobble siempre async y sin lanzar excepciones (best-effort).
- Settings: merge parcial en PUT; defaults creados al primer GET.
- Radio: scoring claro, tiebreak aleatorio.
- Commits convencionales en inglés.

---

*Plan de implementación — Fase 10. Fecha: 2026-08-06.*
