# FEATURE — NeonVibe: crear playlist desde el modal + orden de pistas

**Fase:** 4 — Atajos de biblioteca y ordenación de listas
**Estado:** Cerrada — desplegada y validada en producción (2026-11-06)
**Origen:** petición del propietario (2026-11-06)
**Alcance:** frontend React + un hook de mutación; **sin cambios de API ni de BD**
**No es:** un rediseño de playlists ni un sistema de filtros avanzados por
columna.

---

## 1. Objetivo

1. **Crear una playlist sin salir del modal «Añadir a playlist»**: escribir un
   nombre y que la canción se añada a la playlist recién creada.
2. **Ordenar las pistas de álbumes y playlists** por los criterios habituales
   (título, artista, álbum, duración, año), manteniendo el orden actual como
   opción por defecto.

---

## 2. Estado actual (diagnóstico)

- `AddToPlaylistSheet` solo lista las playlists propias; si no hay ninguna, dice
  «Créala desde la pestaña Playlists». No permite crear.
- El backend ya expone `POST /playlists` y `POST /playlists/{id}/tracks`; los
  hooks `useCreatePlaylist` y `useAddTrackToPlaylist` existen. **No hace falta
  tocar backend.**
- `AlbumDetailPage` carga todas las pistas del álbum (`getAlbumTracks` pagina
  internamente hasta completar) y las pinta en el orden del backend
  (`track_number`, luego `id`).
- `PlaylistDetailPage` carga la playlist completa con sus pistas en el orden
  persistido (`position`) y permite reordenar con flechas.
- Por tanto, la ordenación puede hacerse **en cliente**, sin peticiones nuevas en
  las listas completas. La biblioteca de canciones sí es infinita/paginada y
  necesita orden **en servidor**.

### 2.1 Inventario de listas de pistas

| Lista | Carga | Ordenación |
|---|---|---|
| Álbum (detalle) | completa | cliente |
| Playlist (detalle, dueño) | completa | cliente; reordenado solo en `default` |
| Playlist pública (`/p/:id`) | completa | cliente |
| Artista → Canciones | completa | cliente |
| Favoritos → Canciones | completa | cliente |
| Biblioteca → Canciones | infinita (offset) | **servidor** (`sort` de Pageable) |
| Descargas (offline) | local (IndexedDB) | cliente |
| Selector «Añadir canciones» a playlist | búsqueda paginada | **excluida**: es un buscador |
| Búsqueda → Canciones | infinita | **excluida**: el orden es relevancia |
| Historial | cronológica por día | **excluida**: agrupada por fecha |
| Cola / Now Playing | orden de reproducción | **excluida**: orden funcional |
| Stats (top) | ranking | **excluida**: es un ranking |
| Inicio → recientes | recencia | **excluida**: el orden es el contenido |

> Se aplica el selector en todas las listas donde ordenar no rompe el
> significado de la vista. Las exclusiones quedan documentadas para no
> sorprender.

---

## 3. Diseño — creación inline de playlist

### 3.1 Flujo

1. En el modal, acción **«Nueva playlist»** (botón con `+`).
2. Se despliega un formulario compacto: input de nombre + **«Crear y añadir»**.
3. Al enviar:
   - `POST /playlists` con `{ name, is_public: false }`.
   - `POST /playlists/{id}/tracks` con `{ track_id }`.
   - Cerrar el modal y mostrar un toast: «Añadida a «Nombre»».
4. Si no hay playlists, el formulario de creación se muestra directamente (sin
   obligar a ir a `/playlists`).

### 3.2 Detalles

- Nuevo hook `useCreatePlaylistWithTrack` en `hooks/usePlaylists.ts`: crea,
  añade la pista e invalida `['playlists']` y `['playlist', id]`.
- Validación: nombre no vacío (trim); botón deshabilitado mientras se envía.
- Errores con `apiErrorMessage` (403, 400, red).
- `Escape`/clic fuera siguen cerrando el modal (`useModalA11y`).
- Accesibilidad: `<form>` con `label`, `autoFocus` en el input y estado de
  carga con `aria-live`.

---

## 4. Diseño — ordenación de pistas

### 4.1 Criterios

| Valor | Etiqueta | Ámbito |
|---|---|---|
| `default` | «Orden del álbum» / «Orden de la playlist» | Ambos |
| `title-asc` / `title-desc` | «Título (A–Z)» / «Título (Z–A)» | Ambos |
| `artist-asc` / `artist-desc` | «Artista (A–Z)» / «Artista (Z–A)» | Ambos |
| `album-asc` / `album-desc` | «Álbum (A–Z)» / «Álbum (Z–A)» | Playlist |
| `duration-asc` / `duration-desc` | «Duración (menor)» / «Duración (mayor)» | Ambos |
| `year-asc` / `year-desc` | «Año (antiguo)» / «Año (reciente)» | Ambos |

- Se usa `Intl.Collator('es', { sensitivity: 'base', numeric: true })` para
  acentos/números.
- Campos vacíos van al final; empate se resuelve por título y luego `id` (orden
  estable).
- La ordenación es **inmediata y local** en las listas completas.
- En **Biblioteca → Canciones** se envía `sort` al backend
  (`title,asc`…), porque la lista es paginada; cambiar el orden reinicia la
  paginación. El backend ya lo soporta (`PageableSorts` respeta el sort
  explícito).
- En **Descargas** se ordena por título, artista, fecha de descarga o tamaño.

### 4.2 Componente y utilidad

- `utils/trackSort.ts`: tipo `TrackSortKey`, `sortTracks(tracks, key)` puro y
  `trackSortParam(key)` para el `sort` de Spring.
- `utils/downloadSort.ts`: orden de los registros offline.
- `components/SortSelect.tsx`: `<select>` genérico etiquetado («Ordenar por»).
- `components/TrackSortSelect.tsx`: opciones de pistas, variante `includeAlbum`.
- Se muestra solo cuando hay más de un elemento.

### 4.3 Interacción con la cola y el reordenado

- La cola que se pasa a `TrackRow`/`ReorderTrackRow` se construye con el orden
  **mostrado**, de modo que «Reproducir» respeta lo que ve el usuario.
- En playlists, las flechas de reordenar **se ocultan** mientras haya un orden
  distinto de `default` (el índice visual ya no coincide con `position`). Se
  muestra una pista de texto: «Vuelve a “Orden de la playlist” para reordenar».
- Quitar una pista sigue disponible con cualquier orden (se hace por `id`).
- Al volver a `default` se recupera el orden persistido y las flechas.

---

## 5. Bloques de trabajo

| Bloque | Nombre | Depende de | Esfuerzo |
|---|---|---|---|
| **P1** | Hook `useCreatePlaylistWithTrack` + tests | — | S |
| **P2** | UI de creación inline en `AddToPlaylistSheet` + tests | P1 | M |
| **S1** | `trackSort` + `SortSelect`/`TrackSortSelect` + tests | — | S |
| **S2** | Ordenación en `AlbumDetailPage` + tests | S1 | S |
| **S3** | Ordenación en `PlaylistDetailPage` + desactivar reordenado + tests | S1 | M |
| **S4** | Ordenación en `ArtistDetailPage` y `FavoritesPage` + tests | S1 | M |
| **S5** | Ordenación en `PublicPlaylistPage` + tests | S1 | S |
| **S6** | `sort` de servidor en Biblioteca → Canciones + tests | S1 | M |
| **S7** | Ordenación de Descargas (offline) + tests | S1 | S |

---

## 6. Plan de pruebas

- **Utilidad:** orden por título/artista/álbum/duración/año, asc/desc, nulos al
  final, empate estable, `default` no muta el array.
- **Componente:** opciones visibles, `includeAlbum` oculta álbum, `onChange`.
- **Modal:** crear con nombre vacío no llama a la API; crear + añadir llama en
  orden y cierra; error visible; estado vacío ofrece el formulario.
- **Álbum:** al elegir «Artista (A–Z)» las filas cambian de orden; la cola
  refleja el orden mostrado.
- **Playlist:** al ordenar, se ocultan las flechas de mover; al volver a
  `default`, reaparecen y se recupera el orden original.
- **Artista/Favoritos/Playlist pública:** el selector reordena las filas y la
  cola.
- **Biblioteca:** el selector añade `sort` a `useInfiniteTracks`; sin orden
  explícito no se envía el parámetro (default del backend).
- **Descargas:** orden por título/artista/fecha/tamaño.
- `pnpm test` y `pnpm build` verdes; sin cambios en backend.

---

## 7. Criterios de aceptación

- [ ] Desde el modal puedo crear una playlist con nombre y la canción queda
      dentro, sin salir de la vista actual.
- [ ] Si no tengo playlists, el modal me deja crear la primera.
- [ ] Álbum, playlist, playlist pública, artista, favoritos, biblioteca y
      descargas muestran un selector de orden; por defecto respetan el orden
      actual.
- [ ] Ordenar por título/artista/álbum/duración/año funciona donde aplica y la
      cola reproduce en el orden mostrado.
- [ ] En playlists, reordenar solo está disponible en el orden por defecto.
- [ ] En biblioteca el orden es global (servidor), no solo de la página cargada.
- [ ] Sin cambios de API/BD (salvo usar el `sort` ya existente); tests y build
      verdes.

---

## 8. Riesgos y decisiones

| Riesgo / decisión | Mitigación |
|---|---|
| Reordenar con un orden distinto al persistido | Flechas ocultas fuera de `default` + aviso |
| Listas muy grandes | Ya se cargan completas para álbum/playlist; la ordenación es O(n log n) local |
| Metadatos incompletos | Nulos al final y desempate estable |
| Duplicados de playlist | El backend permite nombres repetidos; se mantiene el comportamiento actual |
| Persistencia del orden elegido | No se persiste: al entrar se usa `default` (requisito) |

---

## 9. Estado de ejecución

| Bloque | Estado | Fecha | Notas |
|---|---|---|---|
| P1 | Hecho | 2026-11-06 | Hook combinado crear+añadir |
| P2 | Hecho | 2026-11-06 | Formulario inline + estado vacío |
| S1 | Hecho | 2026-11-06 | `trackSort`, `downloadSort`, `SortSelect`, `TrackSortSelect` |
| S2 | Hecho | 2026-11-06 | Álbum |
| S3 | Hecho | 2026-11-06 | Playlist + reordenado condicionado |
| S4 | Hecho | 2026-11-06 | Artista y Favoritos |
| S5 | Hecho | 2026-11-06 | Playlist pública |
| S6 | Hecho | 2026-11-06 | Biblioteca → Canciones (sort de servidor) |
| S7 | Hecho | 2026-11-06 | Descargas offline |
| Verificación | Hecho | 2026-11-06 | `pnpm test` → **383 tests**; `pnpm build` OK. Backend **622 tests** (incluye el anexo) |

**Notas de implementación:**

- El selector es un `<select>` nativo etiquetado («Ordenar por»), accesible y
  táctil (44 px), reutilizado por todas las listas.
- En la biblioteca el `sort` se envía al backend solo cuando no es `default`, de
  modo que el orden inicial sigue siendo el estable del servidor.
- La cola de reproducción se construye con la lista mostrada, así que respeta el
  orden elegido.
- En playlists, `ReorderTrackRow` acepta `canReorder`; las flechas desaparecen
  con un orden distinto de `default` pero «Quitar» sigue disponible.
- Exclusiones documentadas: búsqueda (relevancia), historial (cronología), cola
  (orden de reproducción), stats (ranking) e inicio/recientes (recencia).

---

## 10. Anexo — falso error al editar metadata (2026-11-06)

**Síntoma:** al guardar la metadata de un single, el diálogo mostraba
«An unexpected error occurred», pero el cambio sí quedaba guardado tras recargar.

**Diagnóstico:** el mensaje es el 500 genérico del backend. El guardado escribe
primero el fichero y luego actualiza la BD; hay dos vías reales de falso fallo:

1. El PUT ya se confirmó y una **recarga posterior** (invalidación de queries)
   falló; el diálogo la interpretaba como fallo del guardado.
2. El **watcher** veía el MODIFY provocado por la propia escritura de tags y
   reingresaba el fichero a la vez que la transacción de edición, compitiendo por
   las mismas filas de artista/álbum/pista.

**Corrección:**

- `TrackEditButton`, `AlbumEditButton` y `ArtistEditButton` usan
  `Promise.allSettled` para las invalidaciones: un fallo de refresco ya no
  convierte un guardado correcto en error.
- `RecentWrites` registra los ficheros que la app acaba de escribir;
  `MusicScannerService.onFileModified` ignora el MODIFY de esos ficheros. Un
  cambio externo sigue procesándose normal.
- Tests: `RecentWritesTest` y los dos casos de `onFileModified`.

**Validación:** propietario, producción real (2026-11-06). El error desapareció
tras desplegar el build con `Promise.allSettled` + `RecentWrites`; la edición de
metadata de un single se guarda y el diálogo se cierra sin falso error.
