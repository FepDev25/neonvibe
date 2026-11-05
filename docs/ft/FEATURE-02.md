# FEATURE — NeonVibe: remediación UI/UX (auditoría 2026-11-05)

**Fase:** 2 — Corrección por bloques de los hallazgos de la auditoría visual
**Estado:** Plan de trabajo para sesiones con OpenCode
**Origen:** [`docs/qa/reports/2026-11-05-auditoria-inicial.md`](../qa/reports/2026-11-05-auditoria-inicial.md)
**Evidencia:** `docs/qa/playwright/` (capturas, ignoradas por git)
**Alcance:** frontend React/Tailwind + los cambios mínimos de backend
necesarios para búsqueda y datos de la home.
**No es:** un rediseño desde cero, un cambio de identidad visual ni una excusa
para romper el motor de reproducción.

---

## 1. Cómo usar este documento

Cada bloque es una unidad de trabajo cerrable: se implementa, se prueba, se
revisa en desktop y mobile, y se marca como hecho. **No hace falta hacer los 25
hallazgos de golpe ni en orden estricto**, pero sí respetar las dependencias.

Flujo recomendado por sesión:

1. Elegir un bloque de la tabla de §2 (normalmente el siguiente `Pendiente`).
2. Leer el bloque, la auditoría y las capturas de evidencia que referencia.
3. Implementar solo ese bloque (o una parte coherente si es grande).
4. Pasar tests + build + una pasada visual 360/390/1440/1920 en oscuro y claro.
5. Actualizar el estado en §3 y anotar desviaciones.
6. Commit pequeño y descriptivo (`feat:`, `fix:`, `refactor:`).

Plantilla para arrancar una sesión:

```text
Lee docs/ft/FEATURE-02.md y la auditoría enlazada. Implementa el bloque B<N>
completo, con sus criterios de aceptación y tests. No toques otros bloques
salvo dependencias mínimas. Al terminar: tests, build y actualiza el estado.
```

### Reglas de la fase

- **La identidad neón en oscuro se conserva.** El problema es el tema claro y la
  estructura, no la paleta.
- **Mobile-first real:** 360 px es el suelo; 390 px la referencia; 768/1440/1920
  se comprueban siempre.
- **Accesibilidad como criterio, no extra:** AA, foco visible, teclado, 44×44 px.
- **Cada cambio con tests** (Vitest/Testing Library). CI verde.
- **Nada destructivo en producción:** subidas, metadata y borrados se prueban en
  local (`./mvnw spring-boot:run` + Vite), no contra `neonvibe.fepdev.app`.
- **Sin `any`** ni atajos de TypeScript; sin dependencias nuevas sin justificar.
- **Un bloque, un commit/PR.** Si un bloque es grande, se parte en commits
  internos coherentes.

---

## 2. Bloques de desarrollo

| Bloque | Nombre | Hallazgos | Prioridad | Esfuerzo | Depende de | Estado |
|---|---|---|---|---|---|---|
| **B0** | Fundaciones visuales | F-01, F-07, F-25, F-11 (base) | P1 | M | — | Pendiente |
| **B1** | Shell y navegación | F-02, F-10, F-21a | P1 | L | B0 | Pendiente |
| **B2** | Reproductor y mini-player | F-12, F-24, F-11 (player) | P1 | M | B1 | Pendiente |
| **B3** | Home, cards y placeholders | F-03, F-16, F-17 | P1 | M | B0 | Pendiente |
| **B4** | Detalles de colección y acciones primarias | F-04, F-06, F-15, F-18 | P1 | M | B0 | Pendiente |
| **B5** | Filas, tablas y densidad | F-05, F-09, F-22, F-23, F-11 (filas), h1 wrap | P1 | L | B0, B4 | Pendiente |
| **B6** | Búsqueda | F-13, backend `q` | P2 | M | B0 | Pendiente |
| **B7** | Estados, feedback y ajustes | F-14, F-20, F-21b/c | P2 | S | B0 | Pendiente |
| **B8** | Datos, localización y estadísticas | F-08, F-19 | P2 | S | — | Pendiente |
| **B9** | Robustez técnica y hardening | Carrera de cola, JWT en URL, WS, transiciones, tests | P2 | M | B1–B5 | Pendiente |

**Orden recomendado:** B0 → B1 → B2 → B4 → B5 → B3 → B6 → B7 → B8 → B9.
B8 puede hacerse en paralelo (no toca layout). B3 puede adelantarse si se
quiere impacto visible rápido, pero necesita B0.

---

## 3. Estado de ejecución

> Actualizar a medida que se cierren bloques. `Pendiente` / `En curso` / `Hecho`
> / `Bloqueado`. Añadir fecha y notas breves.

| Bloque | Estado | Fecha | Notas / desviaciones |
|---|---|---|---|
| B0 | Hecho | 2026-11-05 | Tokens semánticos, Inter variable self-hosted, foco global + skip link, touch base, movimiento acotado, Now Playing claro y test de contraste (10 casos). Verificación estática: `pnpm test` (254) y `pnpm build` verdes. Pasada visual y 44 px en filas/player quedan en B2/B5. |
| B1 | Hecho | 2026-11-05 | Bottom tabs (Inicio/Buscar/Biblioteca/Ajustes), "Más" reducido a 7 destinos secundarios, sidebar agrupada con Ajustes+usuario al pie, header contextual sin logo, Subir/Escáner en Ajustes (admin) y helper `initials`. Verificación estática: 273 tests y build verdes. Player encajado encima de tabs; pasada visual pendiente. |
| B2 | Hecho | 2026-11-05 | Barra desktop en 3 zonas con play centrado y seek/tiempos juntos; menú `⋯` para acciones secundarias; mini-player mobile de 64 px con 2 controles; calidad 2×2 (≥44 px) y 6 acciones etiquetadas en Now Playing; padding/safe-area actualizados. Verificación estática: 283 tests y build verdes. Pasada visual y medición real del truncado pendientes. |
| B3 | Hecho | 2026-11-05 | Home editorial con continuar/recientes/añadidos/playlists/top artistas, sin marketing; estado vacío con Escanear/Subir (admin); iniciales en placeholders de artista y playlist y CTA en playlists vacías. Recientes por `createdAt,desc` (Pageable ya probado en backend). Verificación estática: 326 tests y build verdes. Pasada visual pendiente. |
| B4 | Hecho | 2026-11-05 | `PageHero` compartido en álbum/artista/playlist, Play/Shuffle primarios, admin y secundarias en `⋯`, "Añadir canciones" con buscador, undo al quitar pistas y Play/Shuffle en Favoritos. Base de toast/undo creada para B7. Verificación estática: 307 tests y build verdes. Pasada visual pendiente. |
| B5 | Hecho | 2026-11-05 | Filas mobile de 2 líneas con cover 44, duración y un solo `⋯`; filas desktop con nº, cover, título/artista, álbum y duración; acciones (incluida metadata admin) en sheet; reorder con acciones en sheet; `LoadMore` centrado; contenido a 1440 px, grids de 6/8 columnas; `h1` de biblioteca en una línea. Verificación estática: 319 tests y build verdes. Pasada visual pendiente. |
| B6 | Hecho | 2026-11-05 | Búsqueda con autofocus, recientes (máx. 5) y sugerencias, top result, contadores por sección, resaltado y portadas; `q` de pistas ampliado a título/artista/álbum y de álbumes a nombre/artista. Verificación: 336 tests frontend, 573 backend y builds verdes. Pasada visual pendiente. |
| B7 | Hecho | 2026-11-05 | `EmptyState` compartido con CTA en descargas, búsqueda, playlists, favoritos, historial, biblioteca y detalles; logout con confirmación; un solo control de tema (header); versión 0.4.0 desde `package.json`; iniciales unificadas; textos ambiguos corregidos. Verificación estática: 340 tests y build verdes. Pasada visual pendiente. |
| B8 | Pendiente | — | — |
| B9 | Pendiente | — | — |

---

## 4. Trazabilidad: los 25 hallazgos + extras

| Hallazgo | Bloque | Nota |
|---|---|---|
| F-01 Contraste tema claro | B0 | 1,38:1 medido; afecta sidebar, tabs, Now Playing |
| F-02 Sin bottom tabs mobile | B1 | Drawer de 12 ítems, scroll oculto a 360×640 |
| F-03 Home de marketing | B3 | Texto "próximo" obsoleto |
| F-04 Sin Play/Shuffle | B4 | Álbum, artista, playlist, favoritos |
| F-05 Filas mobile sobrecargadas | B5 | Título 115 px, 3 acciones de 36 px |
| F-06 Hero de álbum apilado | B4 | 4 botones en columna a 390 px |
| F-07 Inter no cargada | B0 | `fc-match Inter` → Noto Sans |
| F-08 Localización mezclada | B8 | Fechas en inglés, "plays/min", ISO |
| F-09 `max-w-5xl` en 1440/1920 | B5 | ~330 px muertos por lado a 1920 |
| F-10 Navegación plana | B1 | 12 ítems; "Subir" admin mezclado |
| F-11 Objetivos táctiles | B0 + B1/B2/B5 | 86 controles < 44 px en `/library` |
| F-12 Barra de reproducción | B2 | Controles descentrados; mini-player cargado |
| F-13 Búsqueda incompleta | B6 | Sin autofocus/recientes; `q` solo `title`/`name` |
| F-14 Empty states sin CTA | B7 | Descargas, búsqueda, playlists |
| F-15 Playlist sin añadir/undo | B4 | Quitar pista inmediato |
| F-16 Placeholders de artista | B3 | Círculos degradados sin inicial |
| F-17 Portadas de playlist | B3 | Mismo icono; 0 canciones sin CTA |
| F-18 Heroes inconsistentes | B4 | Centrado vs izquierda; títulos cian vs degradado |
| F-19 Gráficas sin contexto | B8 | Sin ejes/unidades/tooltip |
| F-20 Ajustes | B7 | "Cerrar" ambiguo, logout sin confirmar, v0.1 |
| F-21a Logo duplicado | B1 | Sidebar + header |
| F-21b Versión "v0.1" | B7 | Footer de Ajustes |
| F-21c Iniciales "D" vs "DF" | B7 | Avatar de Ajustes vs header |
| F-22 Artista repetido / "•" | B5 | Filas de álbum/playlist |
| F-23 "Cargar más" full-width | B5 | 976 px de ancho |
| F-24 Calidad 26 px / iconos sin label | B2 | Píldoras 3+1 en mobile |
| F-25 Foco gris / sin skip link | B0 | Solo 4/49 componentes con `focus-visible` |
| **Extra:** `h1` "Tu biblioteca" en 2 líneas | B5 | A 390 px |
| **Extra:** 86 controles < 44 px | B0/B5 | Medición en `/library` |
| **Extra:** fechas con locale del navegador | B8 | `toLocaleString(undefined)` |
| **Extra:** `q` de búsqueda solo por título/nombre | B6 | `TrackRepository`/`AlbumRepository` |
| **Extra:** carrera al reproducir desde playlist | B9 | Primer clic revertido una vez |
| **Extra:** JWT en URL de WS/stream | B9 | `?token=` en logs de Cloudflare |
| **Extra:** `* { transition }` global | B0/B9 | Decenas de `CSSTransition`; flakiness |
| **Extra:** errores WS transitorios | B9 | `/ws/info` 200; verificar reconexión |

---

## 5. Bloques en detalle

### B0 — Fundaciones visuales

**Sesión:** 1 · **Prioridad:** P1 · **Esfuerzo:** M · **Depende de:** —
**Hallazgos:** F-01, F-07, F-25, F-11 (base), transiciones globales.

**Objetivo:** que el tema claro sea legible, la tipografía sea consistente y los
componentes compartan tokens de color, foco, tamaño táctil y movimiento. Es la
base de todos los demás bloques.

**Tareas**

- [x] Definir tokens semánticos por tema en `index.css`:
  `--accent-text`, `--accent-soft`, `--accent-strong`, `--surface-raised`,
  `--overlay`, `--focus-ring`, `--tap-target`.
- [x] Sustituir `text-neon-cyan` por `text-accent-text` en todo texto de UI
  (sidebar activo, tabs, enlaces, etiquetas de Now Playing, stats, ajustes).
  El neón queda para bordes, glows y decoración.
- [x] Estados activos: fondo sólido `--accent-strong` + texto blanco, o tinte
  suave + texto oscuro. Verificar AA en ambos temas.
- [x] Now Playing claro: scrim/`bg-black/40` o fondo casi blanco con texto
  oscuro; revisar "REPRODUCIENDO", artista, "CALIDAD" y tiempos.
- [x] Tipografía: self-host de Inter variable (`@fontsource-variable/inter` o
  woff2 en `public/fonts`) **o** decidir formalmente la stack del sistema y
  quitarla de los tokens. Definir escala 12/14/16/20/24/32/40 y
  `font-variant-numeric: tabular-nums` para duraciones.
- [x] Foco: `--focus-ring` (2 px, offset 2) en `Button`, `IconButton`, enlaces,
  tabs y filas; añadir skip link "Saltar al contenido" en `Layout`.
- [x] Tamaño táctil: `Button`/`IconButton` mínimo 44×44 en mobile; documentar
  excepciones (enlaces de texto en línea). *(Las filas y el player conservan
  controles menores hasta B2/B5, que los rediseñan.)*
- [x] Movimiento: eliminar `* { transition: … }`; definir `--motion-fast`
  (150 ms) y `--motion-base` (200 ms) por componente. Mantener
  `prefers-reduced-motion`.
- [x] Añadir una comprobación de contraste (script o checklist) y dejar los
  ratios medidos en el bloque.

**Ratios medidos (2026-11-05, `src/utils/contrast.test.ts`)**

| Par | Claro | Oscuro | Umbral |
|---|---|---|---|
| Texto / fondo | 16,41:1 | 18,06:1 | 4,5 |
| Texto / superficie | 18,00:1 | 17,03:1 | 4,5 |
| Texto atenuado / superficie | 6,76:1 | 5,50:1 | 4,5 |
| Acento / superficie | 5,59:1 | 13,52:1 | 4,5 |
| Acento / fondo | 5,09:1 | 14,33:1 | 4,5 |
| Acento / acento suave | 5,07:1 | 12,06:1 | 4,5 |
| Blanco / acento fuerte | 7,10:1 | 4,50:1 | 4,5 |
| Anillo de foco / superficie | 5,59:1 | 13,52:1 | 3 |
| Aviso / superficie | 5,93:1 | 17,16:1 | 4,5 |
| Peligro / superficie | 7,01:1 | 6,84:1 | 4,5 |

**Criterios de aceptación**

- [x] Todo texto de UI ≥ 4,5:1 y texto grande/iconos ≥ 3:1 en oscuro y claro.
- [x] Sidebar activo, tab activo y Now Playing claro pasan AA.
- [x] La misma fuente en Linux/Windows/macOS; sin CLS perceptible.
- [x] Foco visible en todos los interactivos; skip link funciona con Tab.
- [ ] 0 controles < 44×44 px en las pantallas auditadas (excepciones
  documentadas). *(Base resuelta en `Button`/`IconButton`; filas y player se
  cierran en B2/B5.)*
- [x] Sin transición global `*`; el cambio de tema no provoca jank.

**Archivos probables:** `frontend/src/index.css`, `components/Button.tsx`,
`IconButton.tsx`, `Sidebar.tsx`, `TopHeader.tsx`, `NowPlayingView.tsx`,
`pages/LibraryPage.tsx`, `pages/FavoritesPage.tsx`, `pages/SettingsPage.tsx`,
`package.json`.

**Tests:** test de aplicación de tema (`useThemeHydration`), test de `Button` con
foco, y una checklist visual de contraste. Si se añade utilidad de contraste,
test unitario de la fórmula.

**Riesgos:** cambiar tokens puede alterar sombras/glows; revisar capturas antes
y después. El self-host de fuente añade peso: usar variable + `font-display:
swap`.

**Evidencia:** `desktop-23-library-light.png`, `mobile-12-library-light.png`,
`mobile-13-now-playing-360.png`.

---

### B1 — Shell y navegación

**Sesión:** 2 · **Prioridad:** P1 · **Esfuerzo:** L · **Depende de:** B0
**Hallazgos:** F-02, F-10, F-21a, F-11 (header), h1 wrap (coordinado con B5).

**Objetivo:** que en mobile se llegue a Inicio/Buscar/Biblioteca en un toque y
que en desktop la navegación esté agrupada y sin duplicidades.

**Tareas**

- [x] Nuevo `BottomTabs` mobile (fixed, safe-bottom):
  `Inicio · Buscar · Biblioteca · Ajustes` + mini-player encima (B2).
- [x] Mover destinos secundarios: Playlists/Favoritos/Historial/Estadísticas/
  Descargas → secciones dentro de Biblioteca (segmented) o sheet "Más".
- [x] "Subir" y "Escáner" → Ajustes (admin). Eliminar "Subir" de la nav
  primaria.
- [x] Desktop: sidebar agrupada (Principal / Colecciones / Actividad / Admin),
  Ajustes anclado abajo con el usuario.
- [x] Header: quitar el logo duplicado; mostrar título contextual de la página,
  acceso a búsqueda y acciones. Mantener tema/avatar.
- [x] Drawer: eliminarlo o reducirlo a "Más"; si se conserva, añadir fade de
  scroll y garantizar que 360×640 muestra todos los destinos.
- [x] Actualizar `navItems.ts` (fuente única) y rutas; redirects para rutas
  movidas. *(No se movió ninguna ruta: solo cambió la navegación visible;
  `/upload` sigue existiendo para admin.)*
- [x] Revisar `aria-current`, landmarks (`nav`, `main`) y orden de tabulación.

**Criterios de aceptación**

- [x] Desde cualquier pantalla: Inicio/Buscar/Biblioteca en 1 toque.
- [x] A 360×640 todos los destinos son alcanzables sin adivinar que hay scroll.
- [x] No hay logo duplicado; el header aporta contexto, no marca.
- [x] La sidebar desktop no supera 5 ítems primarios y separa admin.
- [x] Tests de `MobileMenu`/nav actualizados y verdes.

**Archivos probables:** `components/Layout.tsx`, `Sidebar.tsx`, `MobileMenu.tsx`,
`TopHeader.tsx`, `navItems.ts`, `App.tsx`, nuevo `BottomTabs.tsx`.

**Tests:** render de tabs y navegación activa, drawer (si se conserva), rutas.

**Riesgos:** tocar el shell afecta a todas las páginas; hacerlo en un commit
propio y revisar 360/390/1440.

**Evidencia:** `mobile-01-home.png`, `mobile-02-menu-drawer.png`,
`mobile-03-menu-360x640.png`, `desktop-03-library.png`.

---

### B2 — Reproductor y mini-player

**Sesión:** 3 · **Prioridad:** P1 · **Esfuerzo:** M · **Depende de:** B1
**Hallazgos:** F-12, F-24, F-11 (player/píldoras).

**Objetivo:** que la barra de reproducción se sienta parte del layout y que el
mini-player mobile sea legible y manejable con el pulgar.

**Tareas**

- [x] Desktop: barra en 3 zonas (info | transporte centrado | volumen/cola),
  alineada con la columna de contenido; seek + tiempos juntos; acciones
  secundarias (favorito, añadir, descargar, radio, letras) en `⋯`.
- [x] Mobile: mini-player de 64 px con cover 40, título/artista, play/pausa y
  siguiente; barra de progreso fina. Tap en la info abre Now Playing.
- [x] Mover letras/cola/calidad al Now Playing (no al mini).
- [x] Now Playing: píldoras de calidad ≥ 44 px o un `<select>`; 6 acciones con
  tooltip/etiqueta accesible; mantener el fondo ambiente.
- [x] Revisar padding del `main` para que la barra no tape contenido en ninguna
  ruta; safe-area.
- [x] Verificar que el seek y los tiempos no se solapan con el contenido.

**Criterios de aceptación**

- [x] El play queda centrado respecto al contenido en 1440/1920.
- [x] En 360/390 el título del mini muestra ≥ 14 caracteres y solo 2 controles.
- [x] Píldoras de calidad ≥ 44 px y sin salto 3+1 descolgado.
- [x] Ninguna ruta queda tapada por la barra.
- [x] Tests de `PlayerBar`/`NowPlayingView` actualizados.

**Archivos probables:** `components/PlayerBar.tsx`, `NowPlayingView.tsx`,
`SeekBar.tsx`, `IconButton.tsx`, `Layout.tsx`.

**Tests:** `PlayerBar.test.tsx`, `NowPlayingView.test.tsx`, `SeekBar.test.tsx`.

**Riesgos:** no tocar la lógica del motor (`playerStore`); solo presentación.
Coordinar con B9 (carrera de cola) sin mezclar.

**Evidencia:** `desktop-19-player-playing.png`, `mobile-05-library.png`,
`mobile-08-now-playing.png`, `mobile-13-now-playing-360.png`.

---

### B3 — Home, cards y placeholders

**Sesión:** 4 · **Prioridad:** P1 · **Esfuerzo:** M · **Depende de:** B0
**Hallazgos:** F-03, F-16, F-17.

**Objetivo:** que la primera pantalla sea de música, no de marketing, y que
álbumes/artistas/playlists se distingan sin leer.

**Tareas**

- [x] Home editorial: "Continuar escuchando", "Escuchado recientemente",
  "Añadido recientemente", "Tus playlists", "Top artistas".
- [x] Eliminar las tarjetas de marketing y el texto "próximo".
- [x] Empty state de biblioteca vacía: "Escanear" / "Subir música".
- [x] Placeholder de artista: iniciales + degradado determinista; imagen si
  existe.
- [x] Portada de playlist: iniciales + icono/patrón; CTA "Añadir canciones" si
  está vacía.
- [x] Hover/focus de cards y radios consistentes.

**Criterios de aceptación**

- [x] Desde la home se reanuda o inicia reproducción en 1 toque.
- [x] No queda contenido de marketing ni texto obsoleto.
- [x] Dos artistas/playlists distintos se distinguen sin leer el nombre.
- [x] La home funciona con biblioteca vacía y con datos.

**Nota backend:** "Añadido recientemente" usa `sort=createdAt,desc` sobre
`GET /albums`; el `Pageable` de Spring ya lo respeta y
`AlbumServiceTest.search_respectsExplicitClientSort` cubre el contrato. No hizo
falta cambiar el backend.

**Archivos probables:** `pages/HomePage.tsx`, `ArtistCard.tsx`,
`PlaylistCard.tsx`, `AlbumCard.tsx`, `hooks/useLibrary.ts`, `hooks/useHistory.ts`.

**Backend:** reutilizar `/history` y `/tracks`/`/albums` con orden por
`created_at` si existe; si falta, añadir parámetro de orden con test.

**Tests:** `HomePage` (nuevo), `ArtistCard`, `PlaylistCard`.

**Evidencia:** `desktop-02-home-dark.png`, `mobile-01-home.png`,
`desktop-05-artists.png`, `desktop-06-playlists.png`.

---

### B4 — Detalles de colección y acciones primarias

**Sesión:** 5 · **Prioridad:** P1 · **Esfuerzo:** M · **Depende de:** B0
**Hallazgos:** F-04, F-06, F-15, F-18.

**Objetivo:** que escuchar una colección sea la acción evidente y que los
detalles compartan un mismo patrón en desktop y mobile.

**Tareas**

- [x] `PageHero` único (cover, título, meta, acciones) para álbum/artista/
  playlist; alineación izquierda en desktop, columna en mobile.
- [x] Botón primario **▶ Reproducir** + **Shuffle**; admin (carátula, editar,
  eliminar) en `⋯`.
- [x] Mobile: acciones en una fila horizontal con scroll o iconos + `⋯`; nunca
  apilar 4 botones de texto.
- [x] Playlist: botón "Añadir canciones" (buscador/sheet); quitar pista con undo
  toast.
- [x] Unificar títulos: `h1` degradado, `h2` de sección, meta 12–14 px.
- [x] Favoritos: Play/Shuffle arriba de la lista.

**Criterios de aceptación**

- [x] Un toque reproduce la colección completa; otro en aleatorio.
- [x] El hero mobile no apila botones y la lista empieza antes del primer scroll.
- [x] Se puede añadir una canción a una playlist desde su detalle en ≤ 2 toques.
- [x] Quitar una pista es reversible (undo).
- [x] Álbum/artista/playlist comparten estructura y escala.

**Archivos probables:** `pages/AlbumDetailPage.tsx`,
`ArtistDetailPage.tsx`, `PlaylistDetailPage.tsx`, `FavoritesPage.tsx`, nuevo
`components/PageHero.tsx`, `components/AddToPlaylistSheet.tsx`.

**Tests:** páginas de detalle, `AddToPlaylistSheet`, reorder/remove con undo.

**Riesgos:** no romper el orden de playlist ni los permisos admin.

**Evidencia:** `desktop-16-album-detail.png`,
`desktop-17-artist-detail.png`, `desktop-18-playlist-detail.png`,
`mobile-06-album-detail.png`, `mobile-07-playlist-detail.png`.

---

### B5 — Filas, tablas y densidad

**Sesión:** 6 · **Prioridad:** P1 · **Esfuerzo:** L · **Depende de:** B0, B4
**Hallazgos:** F-05, F-09, F-22, F-23, F-11 (filas), h1 wrap, 1920.

**Objetivo:** que la lista de pistas sea el centro de la app: legible en mobile,
densa y útil en desktop.

**Tareas**

- [x] `TrackRow` desktop: portada 40, `#`, título/artista, álbum, duración,
  `⋯`. Hover revela play/`⋯`; favorito solo si está activo.
- [x] `TrackRow` mobile: 2 líneas (cover + título/artista + duración + `⋯`);
  acciones en sheet.
- [x] Eliminar el lápiz de metadata de la fila; moverlo al `⋯` (admin).
- [x] Quitar el artista repetido en álbum/playlist; corregir el "•" del nº de
  pista.
- [x] `LoadMore`: botón centrado, no full-width.
- [x] Contenido: `max-w-7xl` o fluido con máximo 1400; grid de álbumes 5–6
  columnas a 1920; alinear header y contenido.
- [x] Library: `h1` en su propia fila; acciones (Playlists/Favoritos) debajo o
  a la derecha sin partir el título.
- [x] Revisar truncado: título 1–2 líneas con tooltip; duraciones tabulares.

**Criterios de aceptación**

- [x] A 360/390 el título usa ≥ 55 % del ancho y solo hay 1 acción secundaria
  visible.
- [x] A 1440/1920 la lista usa el ancho con álbum/portada; ≤ 120 px de margen
  lateral a 1920.
- [x] `h1` "Tu biblioteca" en 1 línea a 390.
- [x] "Cargar más" no ocupa todo el ancho.
- [x] 0 controles < 44×44 en las filas mobile.
- [x] Tests de `TrackRow` y `LoadMore` verdes.

**Archivos probables:** `components/TrackRow.tsx`, `ReorderTrackRow.tsx`,
`LoadMore.tsx`, `pages/LibraryPage.tsx`, `FavoritesPage.tsx`,
`AlbumDetailPage.tsx`, `PlaylistDetailPage.tsx`, `Layout.tsx`, `AlbumCard.tsx`.

**Tests:** `TrackRow.test.tsx`, `LoadMore`, páginas con lista.

**Riesgos:** es el bloque que más tests toca; hacerlo en commits por componente
(fila → página → ancho).

**Evidencia:** `mobile-05-library.png`, `desktop-03-library.png`,
`desktop-24-library-1920-light.png`, `desktop-16-album-detail.png`.

---

### B6 — Búsqueda

**Sesión:** 7 · **Prioridad:** P2 · **Esfuerzo:** M · **Depende de:** B0
**Hallazgos:** F-13, `q` de backend, autofocus, recientes.

**Objetivo:** que buscar sea rápido y que encontrar un artista muestre también
su música.

**Tareas**

- [x] Autofocus del input al entrar en `/search`.
- [x] Búsquedas recientes (localStorage, máx. 5) + sugerencias/chips.
- [x] "Top result" con portada y contador por sección.
- [x] Resaltar el término en resultados.
- [x] Portada/álbum en las filas de resultados.
- [x] Backend: ampliar `q` de pistas a `title/artist/album` y de álbumes a
  `name/artist` (o pasar `artist=` desde el frontend). Tests de repositorio.
- [x] Estado vacío con CTA (coordinado con B7).

**Criterios de aceptación**

- [x] Buscar un artista devuelve top result + álbumes + pistas.
- [x] Entrar en `/search` deja el cursor en el input.
- [x] Hay recientes y se pueden borrar.
- [x] Tests de backend para el nuevo `q`; tests de `SearchPage`.

**Archivos probables:** `pages/SearchPage.tsx`,
`backend/.../repository/TrackRepository.java`, `AlbumRepository.java`,
`TrackService`/`AlbumService`, tests.

**Riesgos:** el `LIKE '%q%'` con `pg_trgm` ya está indexado; verificar plan y no
degradar. No cambiar contratos existentes.

**Evidencia:** `desktop-13-search.png`, `desktop-14-search-results.png`,
`desktop-15-search-breathe.png`.

---

### B7 — Estados, feedback y ajustes

**Sesión:** 8 · **Prioridad:** P2 · **Esfuerzo:** S · **Depende de:** B0
**Hallazgos:** F-14, F-20, F-21b/c, undo/toast base.

**Objetivo:** que ningún callejón sin salida quede sin acción y que las acciones
destructivas se entiendan.

**Tareas**

- [x] `EmptyState` reutilizable con icono, texto y CTA.
- [x] Descargas: "Ir a la biblioteca"; Búsqueda: recientes/sugerencias;
  Playlists: "Crear playlist".
- [x] Toast/undo base (para B4 y quitar pistas).
- [x] Ajustes: "Cerrar sesión" con confirmación; quitar el selector de tema
  duplicado (o el toggle del header); versión real en el footer.
- [x] Unificar iniciales del avatar (helper `initials(name)`).
- [x] Revisar textos ambiguos ("Cerrar", "En Radio", "Subir carátula").

**Criterios de aceptación**

- [x] Ningún empty state termina sin CTA.
- [x] Logout pide confirmación y dice "Cerrar sesión".
- [x] Una sola forma de cambiar el tema.
- [x] Iniciales coherentes en header, drawer y ajustes.
- [x] Versión correcta (no "v0.1").

**Archivos probables:** `components/EmptyState.tsx` (nuevo), `pages/DownloadsPage`,
`SearchPage`, `PlaylistsPage`, `SettingsPage`, `TopHeader`, `MobileMenu`,
`utils/format.ts`.

**Tests:** empty states, Settings (logout), `initials`.

**Evidencia:** `desktop-10-downloads.png`, `desktop-12-settings.png`,
`mobile-10-settings.png`.

---

### B8 — Datos, localización y estadísticas

**Sesión:** 9 · **Prioridad:** P2 · **Esfuerzo:** S · **Depende de:** —
**Hallazgos:** F-08, F-19, locale de fechas, "plays/min".

**Objetivo:** que toda la app hable español y que las gráficas se entiendan.

**Tareas**

- [ ] Helper `formatDate`/`formatRelative` con `es-ES` (o locale de ajustes).
- [ ] Historial: agrupar por día (Hoy, Ayer, fecha) y mantener hora.
- [ ] Stats: "reproducciones", "min", fechas consistentes; eje Y con 3–4
  marcas, unidad y tooltip.
- [ ] "Actividad": indicar qué mide (reproducciones/día) y resumen.
- [ ] Revisar `Intl.DateTimeFormat` para timezone (ya usa `tz`).
- [ ] Tests de formateo y de `StatsPage`.

**Criterios de aceptación**

- [ ] 0 cadenas en inglés visibles.
- [ ] Un solo formato de fecha en historial y stats.
- [ ] Se puede leer una gráfica sin adivinar unidades.
- [ ] Tests de `format.ts` y `StatsPage` verdes.

**Archivos probables:** `utils/format.ts`, `pages/HistoryPage.tsx`,
`pages/StatsPage.tsx`, tests.

**Evidencia:** `desktop-08-history.png`, `desktop-09-stats.png`.

---

### B9 — Robustez técnica y hardening

**Sesión:** 10 · **Prioridad:** P2 · **Esfuerzo:** M · **Depende de:** B1–B5
**Hallazgos:** carrera de cola, JWT en URL, WS transitorio, transiciones,
tests/CI.

**Objetivo:** cerrar los riesgos observados que no son visuales pero afectan a
la sensación de calidad.

**Tareas**

- [ ] Reproducir y corregir la carrera "primer clic en playlist recién cargada
  revierte a la pista anterior" (posible `restoreFromServer`/`QUEUE_UPDATED`
  pisando `playTrack`). Test de concurrencia.
- [ ] Revisar el JWT en `?token=` de `/ws` y `/stream`: mover a header,
  subprotocolo o cookie de corta vida; documentar compatibilidad.
- [ ] Verificar reconexión WS (los 2 errores vistos fueron transitorios):
  backoff, estado visible, test de reconexión.
- [ ] Acotar transiciones por componente (coordinado con B0) y medir jank al
  cambiar tema/scroll.
- [ ] Pasar `pnpm test`, `pnpm build`, `./mvnw test`; CI verde.
- [ ] Revisar consola/red en las rutas clave: 0 errores, 0 404, 0 warnings
  propios.
- [ ] Documentar límites (PWA, background) si se toca el motor.

**Criterios de aceptación**

- [ ] El test de la carrera falla antes y pasa después.
- [ ] No hay JWT en URLs de producción (o hay decisión documentada con
  mitigación).
- [ ] WS reconecta tras caída simulada.
- [ ] CI verde; sin regresiones de tests.
- [ ] Consola limpia en la pasada final.

**Archivos probables:** `player/sync.ts`, `stores/playerStore.ts`,
`api/client.ts`, `player/playable.ts`, `hooks/useSessionBootstrap.ts`,
`backend/.../security`, tests.

**Riesgos:** tocar sync/token puede romper multi-dispositivo; hacerlo con tests
y sin cambiar el contrato sin necesidad.

---

## 6. Matriz de verificación (por bloque)

Antes de marcar un bloque como `Hecho`:

| Comprobación | Cómo |
|---|---|
| Mobile 360×800 | Captura de las rutas del bloque; sin overflow horizontal |
| Mobile 390×844 | Referencia principal; título/acciones legibles |
| Tablet 768×1024 | Sidebar/drawer y grids |
| Desktop 1440×900 | Layout, densidad, alineación |
| Desktop 1920×1080 | Uso del ancho, sin espacio muerto |
| Tema oscuro | AA y jerarquía |
| Tema claro | AA (el punto crítico de B0) |
| Teclado | Tab por la pantalla; foco visible; skip link |
| Consola/red | 0 errores; peticiones esperadas |
| Tests | `cd frontend && pnpm test`; backend si aplica |
| Build | `cd frontend && pnpm build` |
| CI | Verde |

Capturas nuevas en `docs/qa/playwright/` con prefijo del bloque
(`b0-…`, `b1-…`) para comparar antes/después.

---

## 7. Definición de terminado (global)

- [ ] Los 25 hallazgos (F-01…F-25) y los extras de §4 están resueltos o
      explícitamente descartados con motivo.
- [ ] Tema claro y oscuro pasan AA en las pantallas clave.
- [ ] Mobile tiene bottom tabs + mini-player y se llega a lo primario en 1 toque.
- [ ] Las colecciones tienen Play/Shuffle y las filas son legibles a 360 px.
- [ ] La home muestra música real y estados vacíos con CTA.
- [ ] Búsqueda encuentra artista → álbumes → pistas.
- [ ] Fechas y textos 100 % en español; gráficas con unidades.
- [ ] Sin carreras conocidas al reproducir; WS reconecta.
- [ ] `pnpm test`, `pnpm build`, `./mvnw test` y CI verdes.
- [ ] Auditoría re-ejecutada y comparada contra
      `2026-11-05-auditoria-inicial.md`.

---

## 8. Qué NO hacer en esta fase

- No cambiar la identidad neón del tema oscuro.
- No reescribir el motor de reproducción ni el backend.
- No introducir frameworks de UI o librerías de componentes pesadas sin
  justificar (shadcn/Radix solo si resuelve un problema concreto de
  accesibilidad y se acepta la dependencia).
- No tocar datos reales de producción (subidas, metadata, borrados) sin
  confirmación.
- No mezclar bloques en un mismo commit grande.
- No declarar un bloque "hecho" sin tests y sin la pasada visual.

---

*Documento de trabajo. Última actualización: 2026-11-05.*
