# Fase 11 — Deploy y Producción: Plan de Implementación

> **Fase:** 11 de 11
> **Base:** `docs/specs/fase-11-deploy-spec.md`
> **Estado:** Infraestructura + login prod. El despliegue real lo ejecuta Felipe en el servidor.

---

## 1. Orden de Pasos

1. **`application-prod.yml`** (secrets por env, graceful shutdown, paths reales).
2. **Frontend login**: `LoginPage` (Google GIS) + gate de auth en `App.tsx`.
3. **Scripts**: `deploy/build.sh`, `deploy/setup.sh`, `deploy/deploy.sh`.
4. **systemd**: `deploy/neonvibe.service` + `deploy/neonvibe.env.example`.
5. **`docs/DEPLOY.md`** (guía completa).
6. **Validación**: build JAR integrado, `shellcheck` a scripts, tests, `pnpm build`.
7. Review + commit.

---

## 2. Secuencia de Archivos

```
backend/src/main/resources/application-prod.yml
frontend/src/pages/LoginPage.tsx            (nuevo)
frontend/src/App.tsx                        (gate de auth)
deploy/build.sh
deploy/setup.sh
deploy/deploy.sh
deploy/neonvibe.service
deploy/neonvibe.env.example
docs/DEPLOY.md
README.md                                    (sección deploy → apunta a docs/DEPLOY.md)
```

---

## 3. Checkpoints de Prueba

| # | Checkpoint | Cómo verificar |
|---|---|---|
| C1 | `pnpm build` + `./mvnw package` generan JAR con estáticos | `dist/neonvibe.jar` + `unzip -l` |
| C2 | `shellcheck` limpio en build/setup/deploy | `shellcheck deploy/*.sh` |
| C3 | `LoginPage` renderiza en prod (sin auth) | build + servir |
| C4 | `--spring.profiles.active=prod` arranca con env | `java -jar ... --spring.profiles.active=prod` (local, DB docker) |
| C5 | `./mvnw test` verdes | Exit 0 |
| C6 | DEPLOY.md cubre server, DB, Cloudflare, rollback, logs | lectura |

---

## 4. Riesgos y Contingencias

| Riesgo | Señal | Plan B |
|---|---|---|
| JAR sin estáticos | 404 en `/` | build.sh copia frontend antes del package |
| Google GIS sin client_id | botón no funciona | LoginPage muestra aviso de configuración |
| systemd no encuentra java | service falla | `setup.sh` detecta ruta con `command -v java` |
| Flyway con DB existente | migración falla | documentar backup + `flyway baseline` en DEPLOY.md |

---

## 5. Reglas de Código

- Scripts bash con `set -euo pipefail`, idempotentes, mensajes claros.
- Nunca commitear secrets (solo `.env.example`).
- `application-prod.yml` sin defaults de secretos.
- Commits convencionales en inglés.

---

*Plan de implementación — Fase 11. Fecha: 2026-08-06.*
