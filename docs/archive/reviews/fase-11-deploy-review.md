# Fase 11 — Deploy y Producción: Review

> **Fase:** 11 de 11
> **Base:** `docs/specs/fase-11-deploy-spec.md`, `docs/plans/fase-11-deploy-plan.md`
> **Estado:** ✅ APROBADO (con fixes aplicados tras revisión)

---

## 1. Verificación (esta PC, JAR prod contra DB docker)

| Checkpoint | Resultado |
|---|---|
| `./mvnw test` | ✅ 118 tests, 0 fallos |
| `./deploy/build.sh` | ✅ JAR único con frontend embebido (`dist/neonvibe.jar`) |
| `emptyOutDir` | ✅ solo assets actuales en el JAR |
| `java -jar ... --spring.profiles.active=prod` | ✅ arranca con env vars |
| `GET /` y SPA deep links (`/library`, `/album/4`, `/p/1`, `/settings`) | ✅ 200 |
| API sin token | ✅ 401 |
| `GET /api/v1/auth/refresh` (método erróneo) | ✅ 405 preservado |
| `/actuator/info` | ✅ 401 (solo health público) |
| Assets + `/sw.js` + manifest MIME | ✅ 200 (`application/manifest+json`) |
| JWT_SECRET débil (`CHANGE_ME`/corto) | ✅ falla al arrancar (WeakKey + guard) |
| `bash -n` scripts | ✅ sintaxis OK |

*(El despliegue real en el servidor Debian lo ejecuta Felipe con `setup.sh` + `deploy.sh`.)*

## 2. Entregables

**Config**
- `application-prod.yml`: secrets por env (sin defaults), graceful shutdown (30s), paths `/srv/Music` y `/var/lib/neonvibe`, Flyway validate.

**Login prod (crítico)**
- `LoginPage` (Google Identity Services → `POST /auth/google`) + gate en `App.tsx`
  (`PROD && !isAuthenticated → Login`). DEV mantiene el auto-login.

**Scripts (`deploy/`)**
- `build.sh` (pnpm → mvn → dist/neonvibe.jar con sha256), `setup.sh` (Java/PostgreSQL/usuario/dirs/unit/secrets generados), `deploy.sh` (JAR atómico + backup `.prev` + restart + health check).
- `neonvibe.service` (user dedicado, hardening, `ReadOnlyPaths=/opt/neonvibe`, graceful stop).
- `neonvibe.env.example`.

**Docs**
- `docs/DEPLOY.md`: arquitectura, requisitos, Google Cloud, build, setup, secrets, Cloudflare (SSL Full recomendado), deploy, verificación, operación, backup, rollback, troubleshooting, seguridad.

## 3. Bugs detectados en esta fase (críticos para prod)

1. **`anyRequest().authenticated()` bloqueaba todo el frontend estático** (`GET /` → 401). Fix: solo `/api/**` (y `/actuator/**`) requieren auth; el resto público.
2. **Sin SPA fallback**: deep links (`/album/4`) → 404 en prod. Fix: `SpaForwardController` (`/**/{path:[^\\.]*}`) → `index.html`, excluyendo `/api`, `/ws`, `/actuator` (preserva 405).
3. **Assets stale en el JAR**: `emptyOutDir: true` en vite.
4. **`/actuator/info` público** por `anyRequest().permitAll()`. Fix: `/actuator/**` authenticated.
5. **Secrets `CHANGE_ME` sin validación**: guard `ProdStartupGuard` + `setup.sh` genera JWT_SECRET/DB_PASSWORD reales.
6. **`/opt/neonvibe` escribible por el usuario de servicio**: root-owned + `ReadOnlyPaths` en el unit.
7. **Manifest MIME**: `application/manifest+json`.

## 4. Hallazgos del revisor y correcciones

Revisor: **APROBAR CON CAMBIOS**. C1 (actuator/info público) ✅, C2 (CHANGE_ME) ✅, C3 (/opt escribible) ✅. Menores aplicados: backup `.prev` en deploy.sh, hardening systemd, SSL Full en DEPLOY.md, comando `flyway baseline` concreto, test `actuatorInfo_requiresAuth`.

## 5. Pendientes para el servidor (Felipe)

- Ejecutar `setup.sh` y `deploy.sh` en el servidor Debian Trixie.
- Configurar Google Client ID (`VITE_GOOGLE_CLIENT_ID`) en el build.
- Configurar Cloudflare DNS + SSL + verificar WS.
- Validar scan de `/srv/Music` y scrobbling real (keys Last.fm).

---

*Review — Fase 11. Fecha: 2026-08-06.*
