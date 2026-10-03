# Fase 6 — Playlists y Favoritos: Plan de Implementación

> **Fase:** 6 de 11
> **Base:** `docs/specs/fase-6-playlists-favorites-spec.md`
> **Estado:** Frontend + extensiones backend puntuales.

---

## 1. Orden de Pasos

1. **Backend — DTOs:** `PlaylistResponse.owner_id`, nuevo `PlaylistDetailResponse`.
2. **Backend — servicios:** `PlaylistService.getForUser` (detalle con tracks), batch `findByIds` en Track/Album/Artist, favoritos enriquecidos en `FavoriteService`.
3. **Backend — controllers:** `GET /playlists/{id}`, `GET /favorites/{tracks,albums,artists}`.
4. **Backend — Flyway:** `V4__drop_playlist_tracks_position_unique.sql`.
5. **Backend — tests:** ajustar `PlaylistServiceTest` (ownerId), añadir casos (detalle público/privado).
6. **Frontend — tipos:** `Playlist`, `PlaylistDetail`, `Favorite`, `FavoriteEntityType`.
7. **Frontend — api:** `playlists.ts`, `favorites.ts`.
8. **Frontend — estado:** `favoritesStore.ts`.
9. **Frontend — hooks:** `usePlaylists.ts`, `useFavorites.ts`.
10. **Frontend — componentes:** `FavoriteButton`, `PlaylistCard`, `PlaylistForm`, `AddToPlaylistSheet`, `ReorderTrackRow`.
11. **Frontend — páginas:** `PlaylistsPage`, `PlaylistDetailPage`, `FavoritesPage` + rutas.
12. **Frontend — integración:** hearts en `TrackRow`/`AlbumCard`/`ArtistCard`, links Home/Library.
13. **Checkpoints de prueba.**

---

## 2. Secuencia de Archivos

### Backend
```
dto/PlaylistResponse.java            (+ownerId)
dto/PlaylistDetailResponse.java      (nuevo)
service/PlaylistService.java         (getForUser + resolver tracks)
service/TrackService.java            (+findByIds)
service/AlbumService.java            (+findByIds)
service/ArtistService.java           (+findByIds)
service/FavoriteService.java         (listas enriquecidas)
controller/PlaylistController.java   (+GET /{id})
controller/FavoriteController.java   (+3 endpoints)
resources/db/migration/V4__drop_playlist_tracks_position_unique.sql
test/service/PlaylistServiceTest.java (ajustar + ampliar)
```

### Frontend
```
types/index.ts                 (+Playlist, PlaylistDetail, Favorite, FavoriteEntityType)
api/playlists.ts               (nuevo)
api/favorites.ts               (nuevo)
stores/favoritesStore.ts       (nuevo)
hooks/usePlaylists.ts          (nuevo)
hooks/useFavorites.ts          (nuevo)
components/FavoriteButton.tsx  (nuevo)
components/PlaylistCard.tsx    (nuevo)
components/PlaylistForm.tsx    (nuevo)
components/AddToPlaylistSheet.tsx (nuevo)
components/ReorderTrackRow.tsx (nuevo)
components/TrackRow.tsx        (heart + botón +)
components/AlbumCard.tsx       (heart overlay)
components/ArtistCard.tsx      (heart overlay)
pages/PlaylistsPage.tsx        (nuevo)
pages/PlaylistDetailPage.tsx   (nuevo)
pages/FavoritesPage.tsx        (nuevo)
pages/HomePage.tsx             (links)
pages/LibraryPage.tsx          (links)
App.tsx                        (rutas)
```

---

## 3. Checkpoints de Prueba

| # | Checkpoint | Cómo verificar |
|---|---|---|
| C1 | `./mvnw test` | Exit 0 |
| C2 | Flyway V4 aplica en DB dev | `flyway_schema_history` version 4 |
| C3 | `pnpm build` (tsc strict) | Exit 0 |
| C4 | Crear playlist via API + GET /playlists con `owner_id` | curl |
| C5 | `GET /playlists/{id}` con tracks completos | curl |
| C6 | Añadir/quitar track + reorder sin violación de constraint | curl |
| C7 | Favorito TRACK/ALBUM/ARTIST: POST, aparece en `/favorites/tracks`, DELETE | curl |
| C8 | Playlist pública de otro user (leer 404 si privada) | curl con 2 tokens |
| C9 | End-to-end UI (dev): hearts, crear playlist, add-to-playlist, reorder | navegador |

---

## 4. Riesgos y Contingencias

| Riesgo | Señal | Plan B |
|---|---|---|
| MapStruct no compila con nuevo campo `ownerId` | error de build | Añadir `@Mapping(target="ownerId", source="userId")` o construir record manualmente |
| Reorder persiste con posición duplicada | state corrupto | El servicio ya ordena por posición; validar en review |
| Race en toggle favorito | duplicado 409 | Guard `isToggling` en store |
| Detalle de playlist con track borrado | null en track | Filtrar `pt.getTrack() != null` |

---

## 5. Reglas de Código

- TypeScript strict, DTOs snake_case fieles al backend.
- Mutations TanStack con invalidación de queries canónicas (`['playlists']`, `['playlist', id]`).
- Optimistic UI solo para favoritos; playlists usan refetch tras mutation.
- Accesibilidad: `aria-pressed`/`aria-label` en hearts y botones de reorder.
- Commits convencionales en inglés.

---

*Plan de implementación — Fase 6. Fecha: 2026-08-06.*
