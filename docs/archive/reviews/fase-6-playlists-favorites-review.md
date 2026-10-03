# Fase 6 — Playlists y Favoritos: Review

> **Fase:** 6 de 11
> **Base:** `docs/specs/fase-6-playlists-favorites-spec.md`, `docs/plans/fase-6-playlists-favorites-plan.md`
> **Estado:** ✅ APROBADO (con fixes aplicados tras revisión)

---

## 1. Verificación End-to-End (esta PC)

| Checkpoint | Resultado |
|---|---|
| `./mvnw test` | ✅ 96 tests, 0 fallos (+3 detalle playlist, +1 reorder inválido) |
| Flyway V4 | ✅ Aplicada en dev (dropped `uq_playlist_tracks_position`) |
| `pnpm build` (tsc strict) | ✅ Exit 0 |
| CRUD playlist (crear/listar/editar/eliminar) | ✅ con `owner_id` |
| Añadir/quitar tracks | ✅ |
| Reorder ↑/↓ | ✅ Sin violación de constraint; 400 si ids inválidos/duplicados |
| Favoritos TRACK/ALBUM/ARTIST | ✅ POST/DELETE + listas enriquecidas `/favorites/{tracks,albums,artists}` |
| Playlist pública ajena | ✅ Legible (200), no editable (404 para mutar) |
| Playlist privada ajena | ✅ 404 (no filtra existencia) |
| Rutas SPA | ✅ `/playlists`, `/playlist/:id`, `/favorites` = 200 |

## 2. Bugs corregidos en esta fase

1. **`ArtistService`-style batch** — favoritos enriquecidos requerían `findByIds` en Track/Album/Artist services (no existían).
2. **`GET /playlists/{id}` no existía** — creado con `PlaylistDetailResponse` (tracks completos) y visibilidad owner/público.
3. **`PlaylistResponse` sin ownership** — añadido `owner_id` para distinguir propias vs públicas ajenas en UI.
4. **Reorder con constraint única** — V4 dropea el índice; el servicio reindexa en app (decisión de `docs/pendientes.md`).
5. **`IllegalArgumentException` → 500** — nuevo handler en `GlobalExceptionHandler` → `400 bad_request` (también `IllegalStateException`).
6. **`requireOwned` filtraba existencia** — lanzaba 401 para playlist ajena; ahora 404, coherente con lecturas.
7. **Mismatch JPA↔Flyway** — `PlaylistTrack` aún declaraba `@UniqueConstraint` de posición tras V4; eliminada del entity (posible fallo de `ddl-auto: validate` en prod).
8. **Reorder sin optimismo ni feedback** — optimistic update en `PlaylistDetailPage` (`setQueryData`), botones ↑/↓ deshabilitados mientras `isPending`, `onError` resincroniza el detalle.
9. **Errores de mutation silenciosos** — `onError` global en QueryClient (console.warn) y comentario corregido en `favoritesStore`.

## 3. Hallazgos del revisor (subagente)

Revisor: **APROBAR CON CAMBIOS**. Bloqueantes atendidos (entidad JPA ↔ V4, reorder optimista + 400, validación de ids huérfanos). Menores diferidos:
- `favoritesStore` no es estrictamente optimista (es pending-guarded) — aceptable, documentado.
- Selector de `FavoriteButton` re-renderiza todos los hearts por toggle — escalable a futuro (Fase 9 polish).
- Faltan toasts de error en UI (solo console.warn) — junto con el sistema de notificaciones (Fase 9/10).

## 4. Verificado OK

- Todas las mutaciones pasan por `requireOwned` (no hay ruta de mutación para no-owners).
- `invalidateQueries(['favorites'])` invalida parcialmente `['favorites','tracks'|'albums'|'artists']` (match por prefijo en TanStack v5).
- `FavoriteButton` dentro de `Link`: `preventDefault+stopPropagation` evita navegación (validado).
- `AddToPlaylistSheet` solo muestra playlists propias (`owner_id === currentUser`).

## 5. Pendientes documentados

- Verificación **visual** en navegador (playlist CRUD, reorder, hearts, tabs favoritos).
- Toast de errores de mutation en UI (pendiente sistema de notificaciones).
- Carátulas de playlist reales (Fase 8).

---

*Review — Fase 6. Fecha: 2026-08-06.*
