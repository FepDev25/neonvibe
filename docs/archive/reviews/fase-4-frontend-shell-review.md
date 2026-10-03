# Fase 4 — Frontend Shell y Tema: Revisión

> **Fase:** 4 de 11
> **Fecha revisión:** 2026-08-06
> **Autor:** subagente frontend (Fase 4)

---

## 1. Resumen

El shell de UI de NeonVibe quedó implementado y verificado: Vite + React 18 +
TypeScript strict + Tailwind v4 + Zustand + React Router v6 + PWA. El setup de
tooling compila, el dev server corre, el build de producción completa y
genera el service worker PWA correctamente.

## 2. Checkpoints automáticos verificados

| # | Checkpoint | Resultado | Evidencia |
|---|---|---|---|
| C1 | `pnpm install` sin errores | ✅ | Falta: 387 paquetes, exit 0 |
| C2 | `pnpm dev` levanta | ✅ | Vite 5.4.21 ready en ~2s en `localhost:5173`; `GET /` → HTTP 200; sin errores en log |
| C3 | `pnpm build` completa | ✅ | exit 0; 1641 módulos; output en `backend/src/main/resources/static` |
| C4 | TS strict typecheck | ✅ | `pnpm exec tsc -b` exit 0, sin `any` sin justificar |
| C5 | Manifest PWA servido | ✅ | `/manifest.webmanifest` → HTTP 200 |
| C6 | Service worker generado | ✅ | `sw.js` + `workbox-*.js` generados por GenerateSW (12 entradas precache, 232 KiB) |
| C7 | Paleta neón compilada | ✅ | CSS compilado contiene `--color-neon-cyan/-pink/-purple/-yellow`, variables `.dark`, breakpoints responsive |
| C8 | Proxy `/api` → backend | ✅ | Configurado en `vite.config.ts` (verificado en config) |

### Detalle de la compilación Tailwind (C7)

El CSS transformado por Tailwind v4 contiene todo lo esperado:

- Paleta neón completa (`neon-cyan`, `neon-pink`, `neon-purple`, `neon-yellow`).
- Variables de tema en `:root` (light) y `.dark` (dark, default).
- Custom utilities `neon-text`, `neon-border`, `neon-glow`, `safe-top`, `safe-bottom`.
- Touch targets: `min-h-[64px]` (bottom nav), `min-w-[48px]`.
- Breakpoints mobile-first (`sm` @40rem, `md` @48rem).
- Scrollbar personalizada (neón).

### Detalle del build (C3)

```
✓ built in 7.92s
PWA v0.20.5
mode      generateSW
precache  12 entries (232.22 KiB)
files generated: sw.js, workbox-9c191d2f.js
```

> Nota: `outDir` apunta fuera del raíz del proyecto
> (`../backend/src/main/resources/static`), por lo que Vite no lo vacía
> automáticamente y advierte con `--emptyOutDir`. Es intencional (build
> integrado para Fase 11). El directorio está gitignored en el backend para no
> versionar artefactos generados.

## 3. Rutas implementadas

| Ruta | Página | Estado |
|---|---|---|
| `/` | HomePage | ✅ Neon branding, cards de features, CTA |
| `/library` | LibraryPage | ✅ Placeholder con skeletons de álbumes |
| `/search` | SearchPage | ✅ Input de búsqueda + placeholder |
| `/settings` | SettingsPage | ✅ Toggle tema + sección auth placeholder |
| `*` | NotFoundPage | ✅ 404 neón |
| `/home` | (redirect a `/`) | ✅ |

## 4. Stores Zustand

| Store | Campos | Persistencia |
|---|---|---|
| `themeStore` | `theme`, `setTheme`, `toggleTheme` | `localStorage` (`neonvibe-theme`) + clase `dark` en `<html>` |
| `authStore` | `isAuthenticated`, `user`, `token`, `setAuth`, `logout` | `localStorage` (placeholder) |
| `playerStore` | `isPlaying`, `currentTrack`, `progress`, `volume`, `play/pause/toggle/seek/setVolume` | No (placeholder para Fase 7) |

## 5. Pendiente: verificación manual en navegador

El entorno de ejecución es headless, por lo que los siguientes criterios de
aceptación de PHASES.md requieren verificación visual en un navegador real:

- [ ] **Layout 375px / 1440px** — se ve bien en ambas resoluciones.
- [ ] **Theme toggle persiste** — alternar y recargar mantiene el tema.
- [ ] **Navegación SPA** — cambiar de ruta sin recarga (dev server HTTP 200 en
      todas las rutas, Router v6 client-side).
- [ ] **Bottom nav fija** — permanece fija, contenido scrollea.
- [ ] **PWA instalable** — Chrome DevTools → Application → Manifest válido
      (el manifest se sirve; falta confirmar "Install" en Android).

> Los mecanismos subyacentes (Router v6, persist de Zustand, manifest
> servido, sw generado) ya están verificados programáticamente.

## 6. Riesgos / decisiones tomadas

| Decisión | Detalle |
|---|---|
| Tailwind v4 (no v3) | Usado `@tailwindcss/vite` + CSS-first `@theme`. Más simple que v3.4; sin PostCSS/autoprefixer separados. Paleta y dark mode verificados en CSS compilado. |
| Build hacia backend static | `outDir` apunta a `backend/src/main/resources/static`; directorio añadido a `.gitignore` del backend para no versionar artefactos. |
| Icons SVG placeholder | PWA usa SVG generados (squares neón). Para producción real se requieren PNG 192/512 con máscara (documentado, Fase 9). |
| proxy `/api` | Apunta a `localhost:8080`, listo para Fase 5. |

## 7. Conclusión

El shell cumple los entregables de Fase 4 (`PHASES.md`): Vite+React+TS strict,
Tailwind neón con dark/light, Router v6, layout mobile-first con bottom nav,
stores Zustand, PWA mínima y README. El setup es reproducible (`pnpm install`
+ `pnpm dev`). Único pendiente real es la verificación visual en navegador
(headless), cuyos mecanismos ya están comprobados.

---

*Documento de revisión — Fase 4. Fecha: 2026-08-06.*
