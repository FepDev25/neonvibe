# Fase 4 — Frontend Shell y Tema: Plan de Implementación

> **Fase:** 4 de 11
> **Base:** `docs/specs/fase-4-frontend-shell-spec.md`
> **Estado:** Implementación frontend pura. No se toca backend Java.

---

## 1. Orden de Pasos

1. **Init pnpm project** — `pnpm init`, `package.json` con deps, `.npmrc`, `.gitignore`.
2. **Vite + React + TS** — instalar deps, `vite.config.ts`, `tsconfig.json`, `index.html`, `main.tsx`.
3. **Tailwind** — instalar, configurar paleta neón, tema dark/light, `globals.css`.
4. **Zustand stores** — `themeStore`, `authStore`, `playerStore`.
5. **Router** — `App.tsx` con rutas, páginas placeholder.
6. **Layout** — `Layout`, `TopHeader`, `BottomNav`.
7. **Theme toggle** — botón global + persistencia.
8. **PWA** — `vite-plugin-pwa`, `manifest.json`, iconos SVG.
9. **Componentes UI** — `Button`, `Card`, `IconButton`, `Skeleton`.
10. **README** frontend.
11. **Tests / checkpoints.**

---

## 2. Secuencia de Creación de Archivos

### Paso 0 — Raíz del proyecto
```
neonvibe/frontend/
├── .gitignore
├── .npmrc
├── package.json
```

### Paso 1 — Vite + React + TS
```
neonvibe/frontend/
├── index.html
├── tsconfig.json
├── tsconfig.node.json
├── vite.config.ts
├── pnpm-lock.yaml         (generado)
└── src/
    ├── main.tsx
    ├── vite-env.d.ts
    └── utils/cn.ts
```

### Paso 2 — Tailwind + tema
```
neonvibe/frontend/src/
├── index.css              (paleta neón, dark/light)
├── types/index.ts
└── stores/themeStore.ts
```

### Paso 3 — Stores
```
neonvibe/frontend/src/stores/
├── themeStore.ts
├── authStore.ts
└── playerStore.ts
```

### Paso 4 — Router + páginas
```
neonvibe/frontend/src/
├── App.tsx
└── pages/
    ├── HomePage.tsx
    ├── LibraryPage.tsx
    ├── SearchPage.tsx
    ├── SettingsPage.tsx
    └── NotFoundPage.tsx
```

### Paso 5 — Layout
```
neonvibe/frontend/src/components/
├── Layout.tsx
├── TopHeader.tsx
└── BottomNav.tsx
```

### Paso 6 — PWA
```
neonvibe/frontend/public/
├── manifest.json
├── icon-192.svg
├── icon-512.svg
└── favicon.svg
```
(+ `VitePWA()` en `vite.config.ts`)

### Paso 7 — Componentes UI
```
neonvibe/frontend/src/components/
├── Button.tsx
├── Card.tsx
├── IconButton.tsx
└── Skeleton.tsx
```

### Paso 8 — README
```
neonvibe/frontend/README.md
```

---

## 3. Checkpoints de Prueba

| # | Checkpoint | Cómo verificar |
|---|---|---|
| C1 | `pnpm install` sin errores | Exit 0, lockfile generado |
| C2 | `pnpm dev` levanta | `localhost:5173`, sin errores de consola |
| C3 | `pnpm build` completa | Exit 0, output en `../backend/src/main/resources/static` |
| C4 | Navegación SPA | Click entre rutas sin recarga |
| C5 | Theme toggle | Alterna dark/light, persiste al recargar |
| C6 | Responsive 375/1440 | Layout correcto en ambos anchos |
| C7 | Bottom nav fija | Nav permanece fija, contenido scrollea |
| C8 | PWA instalable | Manifest válido en Chrome DevTools → Application |

---

## 4. Riesgos y Contingencias

| Riesgo | Señal | Plan B |
|---|---|---|
| Tailwind v4 + dark class-mode problemático | Colores no se aplican, build falla | Descender a Tailwind v3.4 + PostCSS/autoprefixer |
| `vite-plugin-pwa` incompatibilidad de versión | Build de PWA falla | `public/manifest.json` + service worker manual (`cache.addAll`) |
| `pnpm` no en PATH del ambiente | Comando no encontrado | Usar `export PATH="$HOME/.npm-global/bin:$PATH"` |
| Build hacia `backend/.../static` en dev molesto | Dist generada dentro de backend | Aceptar; es solo build, no afecta prod |
| Node 22 vs objetivo Node 20 | Ninguna esperada | Compatible; `engines` en package.json |

---

## 5. Reglas de Código

- TypeScript strict, sin `any` sin justificación.
- Sólo funcional components + hooks.
- Mobile-first (375px base).
- Neon aesthetic: dark default, acentos brillantes, glow sutil.
- Commits convencionales en inglés.

---

*Plan de implementación — Fase 4. Fecha: 2026-08-06.*
