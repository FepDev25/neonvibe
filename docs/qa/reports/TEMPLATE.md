# Auditoría UI/UX — <alcance> — <YYYY-MM-DD>

> Plantilla. Copiar a `docs/qa/reports/YYYY-MM-DD-<alcance>.md` y rellenar.
> Borrar las notas entre `<...>`.

| Campo | Valor |
|---|---|
| Fecha | `<YYYY-MM-DD>` |
| Alcance | `<desktop / mobile / pantallas concretas / flujo>` |
| Entorno | `https://neonvibe.fepdev.app` (`<commit / versión>`) |
| Viewports | `<390x844, 768x1024, 1440x900, 1920x1080>` |
| Temas | `<oscuro, claro>` |
| Evidencia | `docs/qa/playwright/<carpeta>` |

---

## 1. Resumen ejecutivo

`<3–6 líneas: estado general de la interfaz, los 3 problemas que más duelen y
la recomendación principal. Sin jerga.>`

**Veredicto:** `<usable / usable con fricción / necesita rediseño del shell>`

**Top 3:**
1. `<hallazgo>` — `<impacto en una frase>`
2. `<hallazgo>` — `<impacto>`
3. `<hallazgo>` — `<impacto>`

---

## 2. Metodología

- Rutas recorridas: `<...>`
- Estados provocados: `<vacío, cargando, error, offline, reproduciendo...>`
- Flujos completados: `<...>`
- Limitaciones: `<p. ej. no se probó subida para no escribir en producción>`

---

## 3. Hallazgos

### 3.1 Tabla priorizada

| ID | Severidad | Pantalla | Hallazgo | Esfuerzo | Evidencia |
|---|---|---|---|---|---|
| F-01 | P1 | `/library` mobile | `<...>` | S | `library-390.png` |
| F-02 | P2 | Global | `<...>` | M | `home-1440.png` |

Severidad: P0 bloquea · P1 impide flujo · P2 fricción · P3 pulido.
Esfuerzo: S ≤ 2 h · M ≤ 1 día · L > 1 día.

### 3.2 Detalle

#### F-01 — `<título corto>`

- **Dónde:** `<ruta / componente>`, viewport `<...>`, tema `<...>`
- **Pasos:** `<1. 2. 3.>`
- **Esperado:** `<qué debería pasar>`
- **Real:** `<qué pasa, con evidencia>`
- **Impacto:** `<por qué importa para el usuario>`
- **Propuesta:** `<cambio concreto; componente/archivo si se conoce>`
- **Criterio de aceptación:** `<verificable, p. ej. "sin scroll horizontal a 360 px">`
- **Evidencia:** `docs/qa/playwright/<archivo>.png`

#### F-02 — `<título corto>`

`<misma estructura>`

---

## 4. Quick wins (≤ 1 día)

| # | Cambio | Archivos probable | Ganancia |
|---|---|---|---|
| 1 | `<...>` | `<...>` | `<...>` |
| 2 | `<...>` | `<...>` | `<...>` |

---

## 5. Cambios estructurales

`<Ordenados por impacto. Cada uno con: problema, propuesta, alcance, riesgo.>`

1. **`<Shell / navegación / reproductor>`** — `<...>`
2. **`<Home / búsqueda / biblioteca>`** — `<...>`
3. **`<Estados vacíos / carga / error>`** — `<...>`

---

## 6. Sistema de diseño

`<Solo si hay hallazgos transversales.>`

| Token / patrón | Hoy | Propuesta | Motivo |
|---|---|---|---|
| Escala de espaciado | `<...>` | `<...>` | `<...>` |
| Escala tipográfica | `<...>` | `<...>` | `<...>` |
| Radios | `<...>` | `<...>` | `<...>` |
| Elevación/superficies | `<...>` | `<...>` | `<...>` |
| Movimiento | `<...>` | `<...>` | `<...>` |
| Contraste | `<...>` | `<...>` | `<...>` |

Componentes a unificar: `<Button, IconButton, Card, Sheet, TrackRow...>`

---

## 7. Arquitectura de información propuesta

`<Solo si aplica: mapa de navegación mobile/desktop, bottom tabs vs drawer,
mini-player, jerarquía de la home.>`

```
Mobile:   <Inicio> · <Buscar> · <Biblioteca>   + mini-player persistente
Desktop:  sidebar <...> + contenido <...>
```

---

## 8. Qué NO cambiar

`<Lo que funciona y conviene preservar: identidad neón, paleta, tono, etc.>`

---

## 9. Siguiente paso

- [ ] Validar hallazgos con el propietario y ajustar prioridades.
- [ ] Implementar quick wins (lote 1).
- [ ] Abordar cambios estructurales (lote 2).
- [ ] Re-auditar `<alcance>` y comparar con este reporte.
