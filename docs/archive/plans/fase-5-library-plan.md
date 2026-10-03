# Fase 5 — Librería y Navegación: Plan de Implementación

> **Fase:** 5 de 11
> **Base:** `docs/specs/fase-5-library-spec.md`
> **Estado:** Frontend integrado contra backend real (dev profile + DB docker).

---

## 1. Orden de Pasos

1. **Fix backend:** `AlbumRepository` + `ArtistService.getAlbums` (buscar por artista). Correr tests.
2. **Tipos:** expandir `src/types/index.ts` (DTOs snake_case + `Page<T>`).
3. **API layer:** `client.ts`, `auth.ts`, `library.ts`, `devBootstrap.ts`.
4. **Hooks:** `useDebounce`, `useInfinitePage`, `useLibrary` (hooks TanStack Query).
5. **Utils:** `format.ts`.
6. **Componentes:** `AlbumCover`, `AlbumCard`, `ArtistCard`, `TrackRow`.
7. **Páginas:** `LibraryPage` (tabs), `AlbumDetailPage`, `ArtistDetailPage`, `AlbumBrowserPage`, `ArtistBrowserPage`, `SearchPage`.
8. **Rutas:** `App.tsx` añade `/album/:id`, `/artist/:id`, `/albums`, `/artists`.
9. **Bootstrap dev:** `devBootstrap` invocado en `App` (solo DEV).
10. **Checkpoints de prueba.**

---

## 2. Secuencia de Archivos

### Fix backend
```
backend/src/main/java/com/neonvibe/repository/AlbumRepository.java   (añadir findByArtistContainingIgnoreCase List)
backend/src/main/java/com/neonvibe/service/ArtistService.java        (usar la nueva query en getAlbums)
```

### Tipos
```
frontend/src/types/index.ts   (Track/Album/Artist completos + Page<T> + AuthResponse)
```

### API layer
```
frontend/src/api/client.ts
frontend/src/api/auth.ts
frontend/src/api/library.ts
frontend/src/api/devBootstrap.ts
```

### Hooks
```
frontend/src/hooks/useDebounce.ts
frontend/src/hooks/useInfinitePage.ts
frontend/src/hooks/useLibrary.ts
```

### Utils
```
frontend/src/utils/format.ts
```

### Componentes
```
frontend/src/components/AlbumCover.tsx
frontend/src/components/AlbumCard.tsx
frontend/src/components/ArtistCard.tsx
frontend/src/components/TrackRow.tsx
```

### Páginas
```
frontend/src/pages/LibraryPage.tsx          (reescrita, tabs)
frontend/src/pages/AlbumDetailPage.tsx
frontend/src/pages/ArtistDetailPage.tsx
frontend/src/pages/AlbumBrowserPage.tsx
frontend/src/pages/ArtistBrowserPage.tsx
frontend/src/pages/SearchPage.tsx           (reescrita)
frontend/src/App.tsx                        (rutas)
```

---

## 3. Checkpoints de Prueba

| # | Checkpoint | Cómo verificar |
|---|---|---|
| C1 | `./mvnw test` pasa (incluye fix artista) | Exit 0 |
| C2 | Backend dev UP + data seed (9 tracks/3 albums/3 artistas) | `/actuator/health`, `/tracks` |
| C3 | `pnpm build` (tsc strict) | Exit 0 |
| C4 | `/library` muestra datos reales | navegador / endpoints |
| C5 | `/album/:id` tracklist | API + navegación |
| C6 | `/artist/:id` álbumes + canciones | API (fix) + navegación |
| C7 | `/search?q=...` filtra server-side | API `?q=` |
| C8 | Scroll infinito carga más | `size` pequeño + sentinel |
| C9 | 401 sin token limpia sesión | interceptor |

---

## 4. Riesgos y Contingencias

| Riesgo | Señal | Plan B |
|---|---|---|
| Shape de `Page` difiere | campos faltantes en TS | Volver a inspeccionar JSON real y ajustar tipos |
| Auth 401 en dev | library vacía | Verificar dev bootstrap; backend dev acepta mock |
| IntersectionObserver no dispara | no carga más | Fallback botón "Cargar más" visible |
| `keepPreviousData` requiere v5 | — | Ya en v5 (`@tanstack/react-query@5`) |

---

## 5. Reglas de Código

- TypeScript strict, sin `any` sin justificación.
- Solo funcional components + hooks.
- Mobile-first (375px), grid responsive.
- Estética neón consistente con Fase 4 (Card, Button, utilities).
- Query keys estables y tipadas; DTOs snake_case exactos.
- Commits convencionales en inglés.

---

*Plan de implementación — Fase 5. Fecha: 2026-08-06.*
