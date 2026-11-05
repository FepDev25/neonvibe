# QA visual y de experiencia — NeonVibe

Playbook para auditar la interfaz real de NeonVibe (desktop y mobile) con el MCP
de Playwright, detectar problemas de usabilidad/consistencia y producir reportes
accionables. No es una suite de tests automáticos: es una revisión de producto
asistida por agente, con evidencia visual.

---

## 1. Entorno

| Elemento | Valor |
|---|---|
| App auditada | `https://neonvibe.fepdev.app` (producción, datos reales) |
| MCP | `qa_neonvibe` (ver `opencode.json`) |
| Navegador | Firefox gestionado por Playwright |
| Perfil | `docs/qa/.browser/` (persistente, **ignorado por git**) |
| Evidencia | `docs/qa/playwright/` (**ignorado por git**) |
| Reportes | `docs/qa/reports/` (versionados) |

Se usa **un único** servidor MCP: el perfil persistente solo puede abrirlo un
navegador a la vez y la app es de un único usuario. El modo mobile se consigue
redimensionando el viewport (`browser_resize`), no con un segundo perfil.

### Por qué Firefox

- No depende de Chrome/Edge instalados ni de la extensión de Playwright.
- El motor de audio y la PWA se comportan de forma representativa.
- El perfil persistente conserva la sesión entre reinicios de OpenCode.

### Por qué `codemode: false`

Las capturas y snapshots del MCP devuelven contenido de imagen. Con Code Mode
los resultados pasan por `execute`; para una auditoría **visual** preferimos las
herramientas nativas, que garantizan que las imágenes llegan al modelo.

### Primera ejecución

El navegador ya está instalado (`~/.cache/ms-playwright/firefox-1569`). Si en el
futuro hay que reinstalarlo:

```bash
npx -y @playwright/mcp@0.0.84 install-browser firefox
```

---

## 2. Autenticación (Google OAuth)

La app guarda la sesión JWT en `localStorage` bajo la clave `neonvibe-auth`
(Zustand `persist`). **No hacen falta cookies de Firefox**: el backend no usa
cookies de sesión, usa `Authorization: Bearer`. Copiar cookies no autenticaría
las llamadas a la API.

### Opción A — storage state (recomendada: el token no pasa por el chat)

1. Abre `https://neonvibe.fepdev.app` en tu Firefox normal, con sesión iniciada.
2. DevTools → Consola:
   ```js
   copy(localStorage.getItem('neonvibe-auth'))
   ```
3. Pega el valor en `docs/qa/.auth/neonvibe-auth.txt`.
4. Genera el fichero de storage state (el token se queda en local):
   ```bash
   node -e 'const fs=require("fs");const v=fs.readFileSync("docs/qa/.auth/neonvibe-auth.txt","utf8").trim();fs.writeFileSync("docs/qa/.auth/storage-state.json",JSON.stringify({cookies:[],origins:[{origin:"https://neonvibe.fepdev.app",localStorage:[{name:"neonvibe-auth",value:v}]}]},null,2))'
   ```
5. Pide al agente que siembre la sesión. Hará:
   - `browser_navigate` a `https://neonvibe.fepdev.app`
   - `browser_set_storage_state` con `docs/qa/.auth/storage-state.json`
   - recargar la página.

A partir de ahí el perfil persistente guarda la sesión y **la renueva solo**: el
access token dura 15 min y el refresh token 7 días, y el cliente lo rota de
forma transparente.

### Opción B — sembrar el valor directamente (rápida)

Si no te importa pegar el blob en el chat: el agente navega a la app,
`browser_localstorage_set` (`key = neonvibe-auth`, `value = <blob>`) y recarga.
Equivalente a la A, pero el token queda en el historial de la conversación.

> **¿Es seguro copiar el token?** Sí. La rotación del backend emite un refresh
> token nuevo pero **no invalida el anterior**; solo se revocan todos al hacer
> logout (`token_version`). Tu navegador y el QA pueden coexistir. Si algún día
> haces logout, ambas sesiones mueren y hay que volver a sembrar.

### Opción C — login manual en el navegador QA (fallback)

El navegador es headed: puedes iniciar sesión con Google a mano en la ventana
que abre Playwright. Google puede bloquear el flujo en navegadores
automatizados; si pasa, usa la Opción A.

### Comprobar la sesión

`browser_localstorage_get` de `neonvibe-auth` debe devolver un JSON con
`isAuthenticated: true`. Si la app muestra la pantalla de login, la sesión
caducó o se revocó: vuelve a sembrar.

---

## 3. Matriz de auditoría

### Viewports

| Nombre | Tamaño | Qué valida |
|---|---|---|
| Mobile S | `360x800` | Móviles pequeños; overflow y textos truncados |
| Mobile | `390x844` | Referencia iPhone 14/15; layout principal |
| Tablet | `768x1024` | Zona intermedia: ¿sidebar o drawer? |
| Desktop | `1440x900` | Sidebar, densidad, uso del ancho |
| Desktop XL | `1920x1080` | Límites de `max-w-5xl`, espacio muerto |

### Temas

- Oscuro (por defecto) y claro.
- Contraste de texto, bordes, foco visible y estados `hover`/`active`/`disabled`
  en ambos.

### Rutas

```
/            /library      /albums      /album/:id
/artists     /artist/:id   /playlists   /playlist/:id
/favorites   /search       /history     /stats
/downloads   /upload       /settings    /p/:id
```

### Estados a provocar

| Estado | Cómo |
|---|---|
| Sin reproducir | Carga inicial, sin track en cola |
| Reproduciendo | Play en una pista; barra + vista Now Playing |
| Pausado | Pausa con pista cargada |
| Cargando | Navegación con red lenta (`browser_route` para demorar `/api`) |
| Vacío | Búsqueda sin resultados, lista/álbum sin datos |
| Error | Ruta inexistente (404), API caída (`browser_route` abort) |
| Offline | `browser_network_state_set` offline |
| Drawer abierto | Menú hamburguesa en mobile |
| Sheets/modales | Cola, letras, añadir a playlist, Now Playing, visualizador |

### Flujos completos (no solo pantallas)

1. Abrir app → biblioteca → reproducir → abrir Now Playing → siguiente.
2. Buscar → filtrar → abrir álbum → reproducir → añadir a playlist.
3. Crear playlist → reordenar → compartir (`/p/:id` en incógnito).
4. Descargar una pista → ver estado en Descargas → offline.
5. Subir un álbum (solo con confirmación: escribe en el servidor).
6. Editar metadata (solo con confirmación: escribe en los ficheros).

---

## 4. Checklist heurístico

Evaluar cada pantalla contra esto; anotar solo lo observable, con evidencia.

### Navegación e IA
- ¿Se entiende dónde estás y cómo volver? Jerarquía de navegación coherente.
- Mobile: ¿hamburguesa vs. bottom tabs? ¿El patrón actual es el convencional
  para una app de música (Inicio / Buscar / Biblioteca + mini-player)?
- Desktop: ¿la sidebar y el contenido comparten ejes y gutters?
- ¿Hay duplicidad de accesos (sidebar + home + header) que compite?

### Jerarquía y layout
- Un solo `h1` por pantalla; jerarquía tipográfica visible (tamaño/peso/color).
- Espaciado en escala consistente (4/8/12/16/24/32); sin valores arbitrarios.
- Alineación y ritmo vertical; nada "flotando".
- Uso del ancho: `max-w-5xl` en 1440/1920, espacio muerto, contenido descentrado.
- Densidad: ¿cabría más información sin asfixiar? ¿Filas de 56–72 px?

### Tipografía
- Escala limitada (display/title/body/caption) y line-height legible.
- Truncado correcto de títulos/artistas largos (`truncate`, 2 líneas máximo).
- Números tabulares en duraciones/tiempos.

### Color y contraste
- Contraste AA (4.5:1 texto normal, 3:1 grande/UI) en ambos temas.
- El neón como acento, no como color de texto de cuerpo.
- Estados semánticos distinguibles (error/aviso/ok) sin depender solo del color.
- Consistencia de superficies: bg / surface / surface-alt / border.

### Componentes y estados
- Botones: jerarquía primary/secondary/ghost, tamaños, icon-only con `aria-label`.
- Foco visible en teclado; hover/active/disabled coherentes.
- Loading con skeletons (no saltos de layout); empty states con acción.
- Errores con recuperación ("Reintentar"), no solo texto rojo.
- Sheets/modales: cierre por backdrop/Esc, foco atrapado, scroll bloqueado.

### Mobile y ergonomía
- Objetivos táctiles ≥ 44x44 px y separación suficiente.
- Safe areas (notch/home indicator) respetadas arriba y abajo.
- Barra de reproducción fija: no tapa contenido, no se solapa con sheets.
- Scroll: sin scroll horizontal accidental, sin `100vh` roto en iOS.
- Pull-to-refresh: no interfiere con el scroll normal.

### Reproductor
- Mini-player: información mínima legible, controles alcanzables con el pulgar.
- Now Playing: jerarquía carátula/título/controles, gesto de cerrar evidente.
- Seek: precisión, feedback, buffering visible.
- Estados de error de reproducción claros y recuperables.

### Accesibilidad
- Navegación completa por teclado; orden de tabulación lógico.
- Landmarks (`header/nav/main`), headings jerárquicos, `aria-current` en nav.
- Imágenes con `alt`; iconos decorativos `aria-hidden`.
- `prefers-reduced-motion` respetado (hay animaciones neón).
- Zoom 200% sin pérdida de contenido.

### Rendimiento percibido
- Sin layout shift al cargar carátulas (aspect-ratio/skeleton).
- Transiciones ≤ 200–300 ms; nada que retrase la interacción.
- La regla global `* { transition: ... }` puede animar propiedades costosas:
  comprobar jank al hacer scroll y al cambiar de tema.

### Consistencia con apps actuales
- Home con contenido real (recientes, añadidos, mixes), no landing de marketing.
- Búsqueda con historial/sugerencias y resultados agrupados.
- Acciones contextuales (⋯) en filas, no botones sueltos por todas partes.
- Feedback inmediato en cada acción (toast, optimistic UI, undo en destructivo).

---

## 5. Severidad

| Nivel | Criterio | Ejemplo |
|---|---|---|
| **P0** | Bloquea el uso o rompe datos | No se puede reproducir; botón que borra sin confirmar |
| **P1** | Impide completar un flujo o es muy confuso | Drawer sin cierre claro; overflow en mobile |
| **P2** | Fricción/ inconsistencia notable | Jerarquía tipográfica plana; estados vacíos sin acción |
| **P3** | Pulido y consistencia | Radios mezclados; hover distinto entre botones |

Cada hallazgo debe indicar **esfuerzo** (S/M/L) y **criterio de aceptación**
verificable.

---

## 6. Reportes

- Un fichero por auditoría: `docs/qa/reports/YYYY-MM-DD-<alcance>.md`
  (ej. `2026-11-05-auditoria-inicial.md`).
- Plantilla: `docs/qa/reports/TEMPLATE.md`.
- Evidencia: capturas en `docs/qa/playwright/`, referenciadas por nombre.
- Cada hallazgo incluye: pantalla, viewport, tema, pasos, esperado vs. real,
  impacto, propuesta concreta (componente/archivo si se conoce) y severidad.
- Al final: quick wins (≤ 1 día), cambios estructurales y una propuesta de
  sistema de diseño (tokens, componentes, patrones de navegación).

### Flujo de trabajo

1. `/qa` (o `/qa solo mobile`, `/qa settings`) → el agente audita y escribe el
   reporte.
2. Revisión conjunta: se aceptan/descartan hallazgos y se prioriza.
3. Implementación por lotes (primero tokens + shell + player, luego pantallas).
4. Re-auditoría con el mismo alcance para comparar contra el reporte anterior.

---

## 7. Límites y seguridad

- Producción tiene **datos reales y un solo usuario**: la auditoría es de solo
  lectura salvo confirmación explícita.
- Flujos destructivos o que escriben en disco (subida, metadata, borrado) se
  prueban contra un entorno local (`./mvnw spring-boot:run` + Vite), nunca
  contra producción sin permiso.
- `docs/qa/.browser/` contiene tokens de sesión: no se versiona ni se comparte.
- El perfil persistente no puede usarse desde dos clientes MCP a la vez.
