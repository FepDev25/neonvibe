# Auditoría UI/UX — inicial (desktop + mobile) — 2026-11-05

| Campo | Valor |
|---|---|
| Fecha | 2026-11-05 |
| Alcance | Shell, navegación, biblioteca, álbumes, artistas, playlists, favoritos, historial, estadísticas, descargas, subida, ajustes, búsqueda, detalle de álbum/artista/playlist, reproductor, Now Playing, cola, letras, login |
| Entorno | `https://neonvibe.fepdev.app` (producción, datos reales) |
| Viewports | 360×800, 390×844, 1440×900, 1920×1080 |
| Temas | Oscuro y claro |
| Evidencia | `docs/qa/playwright/` |

---

## 1. Resumen ejecutivo

NeonVibe **funciona y tiene identidad**: la vista Now Playing (fondo ambiente,
carátula grande, controles) está al nivel de una app comercial, y el tema oscuro
es coherente. Pero el **shell y la arquitectura de información no son los de una
app de música actual**: la home es una landing de marketing, en mobile no hay
navegación inferior (solo un drawer con 12 destinos), las filas de pistas
priorizan acciones de administración sobre el título, y el **tema claro no es
legible** (cian neón sobre blanco = 1,38:1).

**Veredicto:** usable con fricción. No necesita un rediseño total: necesita
(a) arreglar el tema claro, (b) un shell mobile convencional con bottom tabs +
mini-player, (c) una home con contenido real, (d) simplificar filas y acciones,
y (e) acciones primarias "Play all / Shuffle" en cada colección.

**Top 3:**

1. **Tema claro ilegible** — texto cian sobre superficies claras (1,38:1) en
   sidebar activo, tabs y Now Playing. Rompe accesibilidad y la sensación de
   producto cuidado. *(P1)*
2. **Mobile sin patrón de navegación actual** — hamburguesa + drawer de 12
   ítems; sin bottom tabs; el mini-player carga 5 controles y el título queda en
   ~120 px. *(P1)*
3. **Home de marketing y colecciones sin "Play"** — la primera pantalla vende
   la app en vez de mostrar música; álbum/artista/playlist/favoritos no tienen
   botón primario de reproducción. *(P1)*

---

## 2. Metodología

- **Rutas:** `/`, `/library`, `/albums`, `/artists`, `/album/281`, `/artist/32`,
  `/playlists`, `/playlist/4`, `/favorites`, `/search`, `/history`, `/stats`,
  `/downloads`, `/upload`, `/settings`, `/login` (sin sesión).
- **Estados:** sin reproducir, reproduciendo, pausado, cola abierta, letras
  abiertas, Now Playing, drawer abierto, vacío (descargas), búsqueda sin
  resultados, búsqueda con resultados, login.
- **Mediciones objetivas:** contraste WCAG calculado en vivo, desbordamiento
  horizontal, número de objetivos táctiles < 44 px, líneas del `h1`, fuente
  realmente cargada, peticiones de red y consola.
- **Limitaciones:** auditoría de solo lectura. No se probaron subida real,
  edición de metadata ni borrados (escriben en el servidor). No se emuló notch
  ni DPR de dispositivo real. Las capturas `fullPage` muestran la barra del
  reproductor a media página (artefacto de Playwright con `position: fixed`).

---

## 3. Hallazgos

### 3.1 Tabla priorizada

| ID | Sev. | Pantalla | Hallazgo | Esf. | Evidencia |
|---|---|---|---|---|---|
| F-01 | P1 | Global (claro) | Cian neón como texto sobre fondos claros: **1,38:1** | M | `desktop-23-library-light.png`, `mobile-12-library-light.png`, `mobile-13-now-playing-360.png` |
| F-02 | P1 | Mobile (global) | Sin bottom tabs; navegación por hamburguesa con 12 ítems | L | `mobile-01-home.png`, `mobile-02-menu-drawer.png`, `mobile-03-menu-360x640.png` |
| F-03 | P1 | `/` | La home es una landing de marketing; texto "próximo" obsoleto | M | `desktop-02-home-dark.png`, `mobile-01-home.png` |
| F-04 | P1 | Álbum/Artista/Playlist/Favoritos | No hay acción primaria **Play all / Shuffle** | S | `desktop-16-album-detail.png`, `desktop-17-artist-detail.png`, `desktop-18-playlist-detail.png` |
| F-05 | P1 | `/library` mobile | Fila con 7 columnas: título ~115 px y 3 acciones siempre visibles | M | `mobile-05-library.png` |
| F-06 | P2 | `/album/:id` mobile | Las 4 acciones del hero se apilan en vertical junto a la carátula | S | `mobile-06-album-detail.png` |
| F-07 | P2 | Global | Inter declarada pero no cargada → Noto Sans (Linux), Segoe/SF en otros | S | medición `fc-match Inter` |
| F-08 | P2 | Historial/Estadísticas | Fechas en inglés ("Nov 5, 2026, 07:52 AM"), "plays/min" sin traducir, dos formatos de fecha | S | `desktop-08-history.png`, `desktop-09-stats.png` |
| F-09 | P2 | Desktop 1440/1920 | Contenido limitado a `max-w-5xl`: ~330 px muertos por lado a 1920; filas sin álbum ni portada | M | `desktop-24-library-1920-light.png` |
| F-10 | P2 | Sidebar/drawer | 12 ítems planos; "Subir" (admin) mezclado con navegación primaria | S | `desktop-03-library.png`, `mobile-02-menu-drawer.png` |
| F-11 | P2 | Mobile | 86 controles < 44×44 px (acciones de fila 36 px, hamburguesa 36 px, calidad 26 px) | M | medición en `/library` 390 |
| F-12 | P2 | Player (desktop/mobile) | Controles descentrados respecto al contenido; mini-player mobile con 5 controles | M | `desktop-19-player-playing.png`, `mobile-05-library.png` |
| F-13 | P2 | Búsqueda | Sin autofocus, sin recientes/sugerencias, sin contador; buscar artista no muestra sus discos | M | `desktop-13-search.png`, `desktop-14-search-results.png` |
| F-14 | P2 | Descargas/Búsqueda/Playlists | Estados vacíos sin CTA | S | `desktop-10-downloads.png`, `desktop-13-search.png` |
| F-15 | P2 | Playlist | No hay "Añadir canciones"; quitar pista sin confirmación/undo | S | `desktop-18-playlist-detail.png` |
| F-16 | P2 | Artistas | Placeholders idénticos (círculos degradados) sin inicial ni imagen | S | `desktop-05-artists.png` |
| F-17 | P2 | Playlists | Portadas idénticas (mismo icono, solo cambia el degradado); 0 canciones sin CTA | S | `desktop-06-playlists.png` |
| F-18 | P2 | Detalles | Hero centrado en artista vs. izquierda en álbum/playlist; títulos cian vs. degradado | S | `desktop-16/17/18` |
| F-19 | P2 | Estadísticas | Gráficas sin ejes/unidades/tooltip; "Actividad" no dice qué mide | M | `desktop-09-stats.png` |
| F-20 | P2 | Ajustes | "Cerrar" (logout) ambiguo y sin confirmación; tema duplicado con el header | S | `desktop-12-settings.png` |
| F-21 | P3 | Global | Logo duplicado (sidebar + header); "v0.1" obsoleto en Ajustes; iniciales "D" vs "DF" | S | `desktop-02-home-dark.png`, `desktop-12-settings.png` |
| F-22 | P3 | Filas | Artista repetido en cada pista de álbum/playlist; "•" como nº de pista | S | `desktop-16-album-detail.png`, `desktop-03-library.png` |
| F-23 | P3 | Listas | "Cargar más" ocupa 976 px de ancho | S | `desktop-03-library.png` |
| F-24 | P3 | Now Playing | Píldoras de calidad de 26 px y salto de línea 3+1; iconos sin etiqueta en touch | S | `mobile-08-now-playing.png`, `mobile-13-now-playing-360.png` |
| F-25 | P3 | Global | Foco por defecto gris (solo 4/49 componentes con `focus-visible`); sin skip link | S | medición con Tab |

### 3.2 Detalle

#### F-01 — El tema claro no cumple contraste

- **Dónde:** global; sidebar activo, tab activo, Now Playing claro.
- **Esperado:** texto de UI ≥ 4,5:1 (normal) y ≥ 3:1 (grande/iconos).
- **Real:** `#00f3ff` sobre blanco = **1,38:1**. Medido en `a[aria-current]` y en
  el tab activo. En Now Playing claro, "REPRODUCIENDO", "Ghost ·
  Infestissumam" y "CALIDAD" quedan gris claro sobre un degradado cian/rosa muy
  luminoso.
- **Impacto:** el tema claro es la preferencia guardada del propietario; hoy es
  incómodo de leer y falla WCAG AA.
- **Propuesta:** tokens semánticos por tema. En claro, el neón se usa para
  bordes/glows, no para texto:
  ```css
  :root { --accent-text: #0b7285; --accent-soft: #e6f7fa; --accent-strong: #7c3aed; }
  .dark { --accent-text: #00f3ff; --accent-soft: #12202a; --accent-strong: #bc13fe; }
  ```
  Sustituir `text-neon-cyan` por `text-accent-text` en texto; en el tab activo,
  fondo sólido `--accent-strong` + texto blanco (o tinte suave + texto oscuro).
  En Now Playing claro, oscurecer el fondo (scrim `bg-black/40`) o usar texto
  oscuro sobre un fondo casi blanco.
- **Aceptación:** todo texto de UI ≥ 4,5:1 en ambos temas (auditar con
  `axe`/Lighthouse); el tab activo y el sidebar activo pasan AA.

#### F-02 — Mobile sin patrón de navegación convencional

- **Dónde:** todas las rutas a 390 px.
- **Real:** solo hamburguesa → drawer de 12 ítems. A 360×640 los 3 últimos
  ("Descargas", "Buscar", "Ajustes") quedan bajo el pliegue; el `nav` sí
  scrollea, pero no hay indicador y el drawer parece terminar en
  "Estadísticas".
- **Impacto:** las 3 acciones más usadas (Inicio, Buscar, Biblioteca) están
  escondidas; cada cambio de sección cuesta 2 toques y un scroll.
- **Propuesta:** bottom tabs fijas de 3–4 destinos + mini-player encima:
  ```
  [ Inicio ]  [ Buscar ]  [ Biblioteca ]  [ Ajustes ]
  ───────────── mini-player ─────────────
  ```
  Playlists/Favoritos/Historial/Estadísticas/Descargas pasan a secciones dentro
  de Biblioteca (segmented control) o a un sheet "Más". "Subir" y "Escáner"
  quedan en Ajustes (admin). Mantener el drawer solo si se necesita para algo
  secundario.
- **Aceptación:** desde cualquier pantalla se llega a Inicio/Buscar/Biblioteca
  en 1 toque; el drawer deja de ser la única navegación.

#### F-03 — La home no es una home de música

- **Dónde:** `/` en ambos viewports.
- **Real:** hero de marca + 3 tarjetas ("Tu biblioteca", "Tema neón",
  "Reproductor") y botones "Explorar biblioteca / Playlists / Favoritos". El
  texto "Streaming con seek, cola e historial (próximo)" está obsoleto: ya
  existe. A 1440×900 quedan ~400 px vacíos bajo el hero.
- **Impacto:** la primera impresión es de landing page, no de app; no hay nada
  que escuchar ni continuar.
- **Propuesta:** home editorial:
  1. Saludo + "Continuar escuchando" (última pista/cola).
  2. "Escuchado recientemente" (carrusel horizontal de álbumes).
  3. "Añadido recientemente" (grid 4–6).
  4. "Tus playlists" (fila horizontal).
  5. "Top artistas" (fila).
  Si la biblioteca está vacía: empty state con "Escanear" / "Subir música".
- **Aceptación:** en la home se puede reanudar o iniciar reproducción sin salir
  de ella; no queda contenido de marketing.

#### F-04 — Falta la acción primaria de reproducción

- **Dónde:** detalle de álbum, artista, playlist y favoritos.
- **Real:** solo hay reproducción por pista. El hero de álbum ofrece
  "Descargar / Radio / Subir carátula / Editar álbum" (4 secundarias, mismas
  pesos); el de artista "En Radio / Editar artista"; el de playlist "Descargar /
  Editar / Eliminar".
- **Impacto:** la acción más importante (escuchar la colección) no existe; el
  usuario tiene que pulsar pista a pista.
- **Propuesta:** botón primario **▶ Reproducir** (grande, relleno) + **Shuffle**
  secundario junto al título. Las acciones de administración (carátula, editar,
  eliminar) a un menú `⋯`. Mismo patrón en las 4 pantallas.
- **Aceptación:** un toque reproduce la colección completa en orden; otro la
  reproduce en aleatorio.

#### F-05 — Filas de pista sobrecargadas en mobile

- **Dónde:** `/library`, `/favorites`, `/playlist/:id`, `/album/:id` a 390 px.
- **Real:** número + play + título/artista + duración + añadir + favorito +
  editar. El título dispone de **115 px** (30 % del ancho); se trunca
  ("$uicideBoy$ – …", "If You Have Ghost…"). Los 3 iconos ocupan ~100 px y son
  de 36×36 px.
- **Impacto:** no se puede identificar la canción; el dedo falla entre acciones
  contiguas; el lápiz (admin) no debería estar ahí.
- **Propuesta:** en mobile, fila de 2 líneas: portada 40 px + título/artista +
  duración + `⋯`. El `⋯` abre un sheet con favorito, añadir a playlist,
  descargar, radio, letras, editar metadata (admin) y eliminar. En desktop,
  mantener hover + menú contextual.
- **Aceptación:** a 360 px el título usa ≥ 55 % del ancho y solo hay un objetivo
  secundario visible; las acciones siguen accesibles en 1 toque desde `⋯`.

#### F-06 — Hero de álbum roto en mobile

- **Dónde:** `/album/:id` a 390 px.
- **Real:** carátula 128 px + columna de info; las 4 acciones se apilan en
  vertical en una columna de ~140 px, empujando la lista y comiéndose el ancho.
- **Propuesta:** en mobile, hero en columna: carátula 160 px centrada o
  izquierda, título/meta debajo y acciones en una fila horizontal con scroll o
  iconos + `⋯`. Nunca apilar botones de texto.
- **Aceptación:** el hero ocupa como máximo 2 filas de acciones y la lista
  empieza antes del primer scroll.

#### F-07 — Tipografía no es la declarada

- **Real:** `--font-sans` declara `"Inter"`, pero no hay `@font-face` ni carga
  remota; `fc-match Inter` → **Noto Sans**. En Linux se ve Noto; en Windows
  Segoe UI; en macOS SF. La app no tiene una voz tipográfica propia.
- **Propuesta:** self-host de Inter variable (`@fontsource-variable/inter` o
  woff2 en `public/fonts`) con `font-display: swap`; o asumir la stack del
  sistema y quitarla de los tokens. Definir escala (12/14/16/20/24/32/40) y
  `font-variant-numeric: tabular-nums` para duraciones.
- **Aceptación:** la misma fuente en todos los SO; sin CLS al cargar.

#### F-08 — Localización mezclada

- **Real:** historial "Nov 5, 2026, 07:52 AM" (locale del navegador, no de la
  app), estadísticas "10 plays / 36 min" en inglés, y el rango de fechas de
  Estadísticas en ISO (`2026-10-06`). Tres formatos en la misma app.
- **Propuesta:** fijar `es-ES` (o el locale de ajustes) en
  `toLocaleString('es-ES', …)`, traducir "plays" → "reproducciones" y "min" →
  "min", y unificar fechas con un helper `formatDate`/`formatRelative`.
- **Aceptación:** ninguna cadena en inglés visible; un solo formato de fecha.

#### F-09 — Desperdicio de ancho en desktop

- **Real:** `max-w-5xl` (1024 px) dentro de 1440/1920. A 1920 quedan ~330 px
  muertos por lado; las filas no muestran álbum ni portada y el centro queda
  vacío entre título y duración.
- **Propuesta:** `max-w-7xl` (1280) o fluido con máximo 1400; en ≥1280, tabla de
  pistas con portada 40 px, título/artista, álbum, duración y `⋯`; grid de
  álbumes a 5–6 columnas. Alinear el header con la columna de contenido.
- **Aceptación:** a 1920 no hay más de ~120 px de margen lateral y la lista usa
  el ancho con información útil (no espacio vacío).

#### F-10 — Navegación plana y mezclada

- **Real:** 12 ítems con el mismo peso; "Subir" (admin) es el tercero, por
  encima de Álbumes/Artistas/Playlists. En desktop la sidebar tiene 12 filas sin
  grupos; en mobile el drawer ocupa toda la altura.
- **Propuesta:** grupos con etiquetas:
  - **Principal:** Inicio, Biblioteca, Buscar.
  - **Colecciones:** Álbumes, Artistas, Playlists, Favoritos.
  - **Actividad:** Historial, Estadísticas, Descargas.
  - **Admin:** Subir (y Escáner dentro de Ajustes).
  - **Ajustes** anclado abajo, junto al usuario.
- **Aceptación:** la navegación primaria no supera 5 ítems; admin no compite con
  la escucha.

#### F-11 — Objetivos táctiles pequeños

- **Real:** 86 controles < 44×44 px en `/library` 390: acciones de fila 36×36,
  hamburguesa 36×36, cerrar menú 40×40, píldoras de calidad 26 px de alto.
- **Propuesta:** mínimo 44×44 (icon buttons `h-11 w-11`), separación ≥ 8 px;
  agrupar acciones secundarias en `⋯` para no necesitar 3 iconos por fila.
- **Aceptación:** 0 controles interactivos < 44×44 px en mobile (excepto enlaces
  de texto en línea).

#### F-12 — Barra de reproducción

- **Real (desktop):** la barra está limitada a `max-w-5xl`, pero el grupo de
  control queda ~200 px a la derecha del centro del contenido (play en x=1044
  con centro de contenido en x=840). Las etiquetas 0:00/5:31 van a los extremos.
  Hay 6 acciones secundarias siempre visibles.
- **Real (mobile):** mini-player con cover 40 px, título ~120 px ("Ghuleh /
  Z…"), anterior/play/siguiente/letras/cola. El título es ilegible y el pulgar
  compite con 5 controles.
- **Propuesta:** desktop: barra en 3 zonas (info | transporte centrado con seek
  y tiempos juntos | volumen/cola), acciones secundarias en `⋯`. Mobile:
  mini-player de 64 px con cover + título/artista + play/pausa + siguiente; tap
  en la info abre Now Playing (ahí viven letras/cola/calidad). Añadir barra de
  progreso fina.
- **Aceptación:** el play queda en el centro del contenido en desktop; en
  mobile el título muestra ≥ 14 caracteres y solo 2 controles.

#### F-13 — Búsqueda

- **Real:** el input no recibe foco al entrar; no hay búsquedas recientes ni
  sugerencias; no hay contador ni "top result". Buscar "pink" devuelve solo el
  artista Pink Floyd: el `q` de pistas solo busca en `t.title` y el de álbumes
  solo en `a.name` (`TrackRepository`/`AlbumRepository`), así que ni sus discos
  ni sus canciones aparecen. "breathe" devuelve 2 pistas sin portada ni álbum,
  indistinguibles salvo por el artista.
- **Propuesta:** autofocus; recientes (localStorage) + chips de géneros/artistas;
  "Top result" con portada; contador por sección; ampliar el `q` a
  `title/artist/album` (o pasar `artist=` desde la búsqueda); resaltar el
  término; portada en las filas.
- **Aceptación:** buscar un artista muestra su top result + sus álbumes + sus
  pistas; entrar en `/search` deja el cursor listo.

#### F-14 — Estados vacíos sin salida

- **Real:** Descargas: icono + texto, sin botón. Búsqueda vacía: solo "Empieza a
  escribir…". Playlists con 0 canciones: sin CTA.
- **Propuesta:** cada empty state con una acción primaria ("Ir a la biblioteca",
  "Explorar álbumes", "Crear playlist") y, si aplica, sugerencias.
- **Aceptación:** ningún empty state termina sin un siguiente paso claro.

#### F-15 — Playlist: falta añadir y sobra riesgo

- **Real:** no hay botón "Añadir canciones" en el detalle; solo se añade desde el
  `+` de una pista. El icono de papelera de cada fila elimina al instante, sin
  confirmación ni undo.
- **Propuesta:** botón "Añadir canciones" en el hero (abre buscador/sheet);
  quitar con undo toast ("Canción quitada · Deshacer") y confirmación solo si es
  destructivo de verdad.
- **Aceptación:** se puede añadir una canción desde el detalle en ≤2 toques;
  quitar es reversible.

#### F-16 — Placeholders de artista

- **Real:** círculos con degradado determinista, sin inicial ni imagen; todos
  parecen el mismo blob de color.
- **Propuesta:** inicial (1–2 letras) centrada sobre el degradado, o imagen de
  artista si existe; mismo patrón para playlists (inicial + icono pequeño).
- **Aceptación:** dos artistas distintos se distinguen sin leer el nombre.

#### F-17 — Portadas de playlist idénticas

- **Real:** mismo icono de lista, solo cambia el degradado; "Black Metal",
  "Español" y "Rap" (0 canciones) no tienen CTA.
- **Propuesta:** inicial + patrón/tono estable por nombre; en 0 canciones,
  "Añadir canciones" en la tarjeta.
- **Aceptación:** cada playlist es reconocible de un vistazo.

#### F-18 — Heroes inconsistentes

- **Real:** álbum/playlist alineados a la izquierda; artista centrado. Título de
  álbum/playlist cian; títulos de página con degradado; `h1` de artista 2xl.
- **Propuesta:** un solo `PageHero` (cover + título + meta + acciones) con
  alineación izquierda en desktop y columna en mobile; un solo estilo de `h1`
  (degradado) y un `h2` para secciones.
- **Aceptación:** las 3 pantallas comparten estructura y escala tipográfica.

#### F-19 — Gráficas sin contexto

- **Real:** "Actividad" sin eje Y, sin unidades, sin tooltip; barras que tocan el
  borde superior; "Por hora del día" solo con 5 etiquetas.
- **Propuesta:** eje Y con 3–4 marcas, unidad explícita ("reproducciones"),
  tooltip al hover/tap, y resumen ("39 reproducciones en 30 días"). Mantener el
  estilo de barras degradadas.
- **Aceptación:** se puede responder "¿cuánto escuché el 12?" sin adivinar.

#### F-20 — Ajustes

- **Real:** "Cerrar" hace logout inmediato (revoca el refresh token de todos los
  dispositivos) sin confirmación; el selector de tema duplica el toggle del
  header; el footer dice "v0.1".
- **Propuesta:** "Cerrar sesión" con confirmación; quitar el selector de tema o
  el toggle (uno solo); versión real desde `package.json`/build.
- **Aceptación:** logout pide confirmación y se entiende qué hace.

---

## 4. Quick wins (≤ 1 día)

| # | Cambio | Archivos probables | Ganancia |
|---|---|---|---|
| 1 | Tokens `--accent-text`/`--accent-soft` y sustituir cian en texto claro | `index.css`, `Sidebar.tsx`, tabs, `NowPlayingView.tsx` | Sube a AA en claro |
| 2 | `font-family` real: self-host Inter o stack del sistema | `index.css`, `package.json` | Voz tipográfica consistente |
| 3 | `toLocaleString('es-ES')` + "reproducciones"/"min" | `utils/format.ts`, `StatsPage.tsx` | Quita el inglés |
| 4 | `⋯` en filas mobile y ocultar lápiz/+heart | `TrackRow.tsx`, `ReorderTrackRow.tsx` | Título ≥ 55 % del ancho |
| 5 | Botón "▶ Reproducir" + "Shuffle" en heroes | `AlbumDetailPage`, `ArtistDetailPage`, `PlaylistDetailPage`, `FavoritesPage` | Acción primaria clara |
| 6 | Empty states con CTA | `DownloadsPage`, `SearchPage`, `PlaylistsPage` | Recuperación |
| 7 | Autofocus en búsqueda + recientes | `SearchPage.tsx` | Menos fricción |
| 8 | `h1` de biblioteca en su propia fila | `LibraryPage.tsx` | Deja de partirse en 2 líneas |
| 9 | "Cargar más" como botón centrado (no full-width) | `LoadMore.tsx` | Menos ruido |
| 10 | Iniciales en placeholders de artista/playlist | `ArtistCard`, `PlaylistCard` | Reconocimiento |

---

## 5. Cambios estructurales

1. **Shell mobile con bottom tabs + mini-player** (F-02, F-12). Impacto alto;
   toca `Layout`, `Sidebar`/`MobileMenu`, `PlayerBar` y las rutas. Riesgo medio:
   conservar el drawer para admin o eliminarlo.
2. **Home editorial** (F-03). Requiere endpoints de "recientes" (ya existe
   historial) y "añadidos" (orden por `created_at` en tracks/álbumes).
3. **PageHero unificado + acciones primarias** (F-04, F-18). Un componente,
   cuatro pantallas.
4. **Filas y tabla de pistas** (F-05, F-09, F-22). En desktop tabla con columnas;
   en mobile fila de 2 líneas + `⋯`. Es el cambio que más "sensación de app"
   aporta.
5. **Tema claro de primera clase** (F-01). No es solo un token: revisar
   `neon-glow`, degradados de Now Playing y bordes.
6. **Búsqueda completa** (F-13). Autofocus, recientes, top result y backend por
   artista.
7. **Navegación agrupada en desktop** (F-10). Sidebar con secciones y Ajustes
   anclado abajo.

---

## 6. Sistema de diseño

| Token / patrón | Hoy | Propuesta | Motivo |
|---|---|---|---|
| Acento de texto | `neon-cyan` en ambos temas | `--accent-text` por tema (`#0b7285` claro / `#00f3ff` oscuro) | Contraste AA |
| Superficies | `bg/surface/surface-alt` | Añadir `--surface-raised` y `--overlay` | Cards, sheets, hover |
| Escala tipográfica | 10/11/12/14/16/18/20/24/30/36/48 | 12/14/16/20/24/32/40 (más saltos) | Jerarquía clara |
| Radios | `rounded-lg/xl/2xl/3xl/full` mezclados | 8 (control), 12 (card), 16 (hero), full (pill) | Consistencia |
| Espaciado | 0.5/1/1.5/2/3/4/5/6/8/12 | Escala 4/8/12/16/24/32/48 | Ritmo |
| Movimiento | `* { transition }` global + eq-bounce infinito | Transiciones por componente (150–200 ms) y `motion-reduce` | Rendimiento y foco |
| Elevación | `neon-glow` en casi todo | Glow solo en foco/estado activo | El neón recupera significado |
| Iconos | 16/20/24 mezclados | 16 (inline), 20 (acciones), 24 (nav) | Consistencia |

Componentes a unificar: `Button`, `IconButton`, `Card`, `PageHero`, `TrackRow`,
`Sheet`, `EmptyState`, `SegmentedTabs`, `Toggle`.

---

## 7. Arquitectura de información propuesta

```
Mobile
┌──────────────────────────────┐
│  header contextual (título)  │
│                              │
│  contenido                   │
│                              │
│  ── mini-player 64 px ──     │
│ [Inicio] [Buscar] [Biblioteca] [Ajustes]
└──────────────────────────────┘

Biblioteca (mobile): tabs [Canciones | Álbumes | Artistas | Playlists]
  · Favoritos / Historial / Estadísticas / Descargas → accesos en la parte superior
  · Subir / Escáner → Ajustes (admin)

Desktop
Sidebar 240 px          Contenido max-w-7xl
Principal               Header: título + búsqueda + acciones
  Inicio
  Biblioteca
  Buscar
Colecciones
  Álbumes / Artistas / Playlists / Favoritos
Actividad
  Historial / Estadísticas / Descargas
Admin
  Subir
[Ajustes + usuario] (abajo)
```

---

## 8. Qué NO cambiar

- **La identidad neón en oscuro**: paleta, degradados y glow funcionan; el
  problema es el claro, no el oscuro.
- **Now Playing**: fondo ambiente, carátula grande, jerarquía y controles están
  muy bien; solo hay que resolver el contraste en claro y las píldoras de
  calidad.
- **El motor de reproducción y la PWA**: sin errores de consola relevantes;
  `/ws/info` responde 200 (los 2 errores de WebSocket vistos fueron transitorios
  durante navegación).
- **Las etiquetas `aria-label`**: la mayoría de iconos están bien etiquetados;
  sirven de base para el rediseño.
- **`prefers-reduced-motion`**: ya está contemplado en 3 bloques de CSS.

---

## 9. Notas técnicas observadas

- **Carrera al reproducir desde una playlist recién cargada:** el primer clic en
  "Con Clavi Con Dio" arrancó la pista y, ~2 s después, la barra volvió a la
  pista anterior ("Avantasia (Live)"). El segundo clic ("Elizabeth") sí se
  mantuvo. Probable carrera entre `playTrack` y `restoreFromServer`/`QUEUE_UPDATED`.
  No reproducible de forma consistente; conviene un test.
- **JWT en la URL del WebSocket y del stream** (`?token=`): queda en logs de
  Cloudflare. No es UI, pero conviene moverlo a un header/subprotocolo o a una
  cookie `HttpOnly` de corta vida.
- **Transiciones globales** (`* { transition: … }`) generan decenas de
  `CSSTransition` en cada cambio de estado y hacen que Playwright considere
  inestables algunos elementos (timeouts al hacer clic en la barra). Acotarlas
  por componente mejora rendimiento y automatización.
- **Historial:** `toLocaleString(undefined, …)` usa el locale del navegador; en
  un navegador en inglés la app en español muestra fechas en inglés.

---

## 10. Siguiente paso

**Plan de ejecución por bloques:** [`docs/ft/FEATURE-02.md`](../../ft/FEATURE-02.md)
(10 bloques, con tareas, criterios de aceptación, archivos y tests).

- [ ] Validar hallazgos y priorizar con el propietario.
- [ ] Lote 1 (quick wins 1–5): tema claro, tipografía, localización, filas
      mobile, Play/Shuffle.
- [ ] Lote 2 (estructural): shell mobile con bottom tabs + home editorial.
- [ ] Lote 3: búsqueda, stats, empty states, detalles.
- [ ] Re-auditar con el mismo alcance y comparar contra este reporte.
