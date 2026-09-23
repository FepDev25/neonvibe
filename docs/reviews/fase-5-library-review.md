# Fase 5 — Librería y Navegación: Review

> **Fase:** 5 de 11
> **Base:** `docs/specs/fase-5-library-spec.md`, `docs/plans/fase-5-library-plan.md`
> **Estado:** ✅ APROBADO (con fixes aplicados)

---

## 1. Verificación End-to-End (esta PC)

| Checkpoint | Resultado |
|---|---|
| `./mvnw test` | ✅ 92 tests, 0 fallos (incluye fix de `ArtistService`) |
| `pnpm build` (tsc strict + vite) | ✅ Exit 0, bundle integrado a `backend/.../static` |
| Backend dev profile UP | ✅ `/actuator/health` = UP, Flyway v3 aplicado |
| Seed `/tmp/test-music` + scan | ✅ 9 tracks / 3 álbumes / 3 artistas |
| `GET /artists/{id}/albums` | ✅ Devuelve álbumes del artista (bug corregido) |
| Búsqueda server-side `?q=` | ✅ Filtra tracks/albums/artists |
| Paginación `page`/`size` | ✅ `Page` serializada correctamente |
| Dev proxy Vite (`/api`) | ✅ Login mock, `/me`, `/tracks`, `/albums/{id}/tracks`, `/artists/{id}/albums` |
| Rutas SPA | ✅ `/`, `/library`, `/albums`, `/artists`, `/album/1`, `/artist/1`, `/search` = 200 |

## 2. Bugs corregidos en esta fase

1. **`ArtistService.getAlbums` buscaba por nombre de álbum** — `findByNameContainingIgnoreCase(artistName)` nunca encontraba nada. Fix → `findByArtistIgnoreCase` (matching exacto, case-insensitive).
2. **Falsos positivos con substring** — la revisión detectó que `Containing` traería coincidencias parciales ("AC" → "AC/DC"). Corregido a igualdad exacta.
3. **Mismatch `avatar_url`** — `authStore.AuthUser` usaba `avatarUrl` (camelCase) pero el backend manda `avatar_url`; el avatar quedaba siempre `undefined`. Unificado a `avatar_url`.
4. **`SearchPage` disparaba 3 requests con `q=""`** — sin `enabled`, cada montaje consultaba la primera página completa de cada tipo. Fix → `enabled: debounced.length > 0`.
5. **Interceptor 401 hacía logout en rutas de auth** — ahora exime `/auth/*` del auto-logout (no saboteará el refresh en v0.2).
6. **`TrackRow` tipos** — `playerStore.id: string` vs `Track.id: number` reconciliado con `String()`; `number` prop restringido a `number`.

## 3. Hallazgos del revisor (subagente)

Revisor: **APROBAR CON CAMBIOS**. Todos los bloqueantes atendidos:
- Matching exacto de álbumes por artista (✔ aplicado).
- `enabled` en búsqueda (✔ aplicado).
- Mismatch `avatar_url` (✔ aplicado).
- Interceptor 401 (✔ aplicado).

Mejoras menores diferidas (no bloqueantes, anotadas):
- Semántica de listas (`<ol>/<li>`) para screen readers — aplicar en polish (Fase 9).
- `LoadMore<T>` genérico más estricto (`InfiniteData<Page<T>>`).
- Unificar `AlbumService.search` combinado `q`+`artist` (devolver 400 o soportar ambos) — decidir en Fase 6.
- `fetchMe()` post-mount para validar token persistido en prod — junto con el flujo OAuth real (pendiente).

## 4. Pendientes documentados

- Verificación **visual** en navegador real (375px/1440px, tabs, scroll infinito, skeletons).
- Flujo **OAuth real en prod** (UI de login + redirect Google) — el dev usa bootstrap mock.
- Carátulas reales (Fase 8) — hoy placeholders determinísticos.

---

*Review — Fase 5. Fecha: 2026-08-06.*
