# Fase 11 — Deploy y Producción: Especificación

> **Fase:** 11 de 11
> **Tipo:** Infraestructura (DevOps) + un entregable de frontend crítico (login en prod).
> **Dependencias:** Todas las anteriores (Fase 0-10).
> **Objetivo:** NeonVibe se despliega en el servidor Debian Trixie como JAR + systemd, con frontend embebido, detrás de Cloudflare, con secrets por variables de entorno y una guía reproducible.

---

## 1. Alcance

### Infraestructura (scripts + docs)
- **Build integrado**: el frontend se compila dentro de `backend/src/main/resources/static` y queda embebido en un único JAR (`neonvibe-backend-0.1.0-SNAPSHOT.jar`).
- **`deploy/build.sh`**: compila frontend + backend y genera el JAR (ejecutable en la máquina de desarrollo o el servidor).
- **`deploy/setup.sh`**: prepara el servidor la primera vez (Java 21, PostgreSQL, usuario de servicio, directorios, permisos, systemd unit, `.env`).
- **`deploy/deploy.sh`**: despliegue repetible (subir/copy JAR → restart systemd → health check).
- **`deploy/neonvibe.service`**: unidad systemd (usuario dedicado, variables de entorno, graceful shutdown).
- **`deploy/neonvibe.env.example`**: plantilla de secrets (`JWT_SECRET`, Google OAuth, Last.fm, DB).
- **`application-prod.yml`**: configuración de producción (paths reales, sin defaults de secrets, graceful shutdown).
- **`docs/DEPLOY.md`**: guía paso a paso (requisitos, servidor, PostgreSQL, Cloudflare, DNS, deploy, rollback, logs, troubleshooting).

### Frontend crítico para prod: Login con Google
- El `devBootstrap` **solo** funciona en `import.meta.env.DEV`. En producción el usuario debe autenticarse, así que se añade:
  - **`LoginPage`**: botón de Google (Google Identity Services) que obtiene un `id_token` y lo envía a `POST /api/v1/auth/google`.
  - **Gate de auth**: si `PROD && !isAuthenticated` → se muestra `LoginPage` en lugar de la app.
  - `VITE_GOOGLE_CLIENT_ID` como env de build (se inyecta en el bundle).
- En `DEV` se mantiene el auto-login (sin cambios).

---

## 2. Topología de Producción

```
Internet
   │  https://<dominio>  (TLS terminado en Cloudflare)
   ▼
Cloudflare (proxy + SSL, modo Flexible o Full)
   │  http://<IP-servidor>:8080
   ▼
Debian Trixie (bare-metal)
   ├─ nginx? (OPCIONAL)  ← no se usa; Spring sirve estáticos + API en :8080
   ├─ systemd service neonvibe (JAR, user neonvibe)
   │     ├─ /srv/Music (lectura, música)
   │     ├─ /var/lib/neonvibe/covers + /lyrics (escritura, cache)
   │     └─ /opt/neonvibe/neonvibe.jar + neonvibe.env
   └─ PostgreSQL (nativo, db neonvibe, user neonvibe)
```

- **No se usa Nginx/Caddy**: Spring Boot sirve el frontend estático (`classpath:/static`) y la API en el mismo puerto 8080. Cloudflare termina TLS.
- WebSocket `/ws` pasa por Cloudflare (soportado) → `wss://<dominio>/ws`.
- Música en `/srv/Music` (38 GB, `felipep:felipep`, 755 → legible por el usuario de servicio).

---

## 3. `application-prod.yml`

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/neonvibe}
    username: ${DB_USERNAME:neonvibe}
    password: ${DB_PASSWORD}
  jpa:
    show-sql: false
    hibernate:
      ddl-auto: validate
  lifecycle:
    timeout-per-shutdown-phase: 30s

server:
  port: ${SERVER_PORT:8080}
  shutdown: graceful

neonvibe:
  jwt:
    secret: ${JWT_SECRET}          # sin default → falla si falta
  auth:
    google:
      enabled: true
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS}
  music:
    paths: ${MUSIC_PATHS:/srv/Music}
    scan-interval-seconds: ${SCAN_INTERVAL:0}
  covers:
    cache-path: ${COVERS_CACHE:/var/lib/neonvibe/covers}
  lyrics:
    cache-path: ${LYRICS_CACHE:/var/lib/neonvibe/lyrics}
  lastfm:
    api-key: ${LASTFM_API_KEY:}
    api-secret: ${LASTFM_API_SECRET:}
  frontend-base: ${FRONTEND_BASE}
  websocket:
    allowed-origins: ${WS_ORIGINS}
```

Secrets obligatorios en prod: `DB_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, `FRONTEND_BASE`, `WS_ORIGINS`. Opcionales: `LASTFM_*`.

---

## 4. Scripts

### `deploy/build.sh` (build del JAR)
- `pnpm install --frozen-lockfile && pnpm build` (frontend → `backend/src/main/resources/static`).
- `./mvnw -f backend/pom.xml clean package -DskipTests`.
- Copia `backend/target/neonvibe-backend-0.1.0-SNAPSHOT.jar` → `dist/neonvibe.jar`.
- Imprime sha256 para verificación.

### `deploy/setup.sh` (primera vez, en el servidor, como root)
- Instala OpenJDK 21 + PostgreSQL si faltan.
- Crea usuario de sistema `neonvibe`.
- Crea `/opt/neonvibe`, `/var/lib/neonvibe/{covers,lyrics}`, asigna permisos.
- Crea base de datos y usuario PostgreSQL `neonvibe` (si no existen).
- Instala `deploy/neonvibe.service` en `/etc/systemd/system/`.
- Copia `neonvibe.env.example` → `/opt/neonvibe/neonvibe.env` (si no existe) y pide editarlo.

### `deploy/deploy.sh` (despliegue repetible)
- Acepta el JAR (por defecto `dist/neonvibe.jar`).
- Valida que existe `/opt/neonvibe/neonvibe.env`.
- Copia el JAR a `/opt/neonvibe/neonvibe.jar.new`, lo activa atómicamente (`mv`).
- `systemctl daemon-reload` + `systemctl restart neonvibe`.
- Espera y verifica `GET /actuator/health` hasta `UP`.
- Muestra el estado y el log reciente (`journalctl -u neonvibe -n 30`).

### `deploy/neonvibe.service`
- `User=neonvibe`, `WorkingDirectory=/opt/neonvibe`.
- `EnvironmentFile=/opt/neonvibe/neonvibe.env`.
- `ExecStart=/usr/bin/java -jar /opt/neonvibe/neonvibe.jar --spring.profiles.active=prod`.
- `Restart=on-failure`, `RestartSec=5`, límites de ficheros, `After=network.target postgresql.service`.

### `deploy/neonvibe.env.example`
- Comentario de cada variable + placeholders (`JWT_SECRET`, `DB_PASSWORD`, etc.) + cómo generarlos (`openssl rand -base64 32`).

---

## 5. Cloudflare (documentado en DEPLOY.md)
- DNS: registro `A` (`@` y `www`) → IP del servidor, **proxy activado** (nube naranja).
- SSL/TLS: modo **Flexible** (MVP; Cloudflare termina TLS y habla HTTP al origen 8080) o **Full** (requiere cert en origen). Se documentan ambos y se recomienda Flexible para empezar.
- WebSocket habilitado (por defecto en todos los planes).
- Regla opcional de caché para `/api/v1/*/cover` (ya la maneja el SW del cliente; opcional).

---

## 6. Criterios de Aceptación (de PHASES.md)
1. `systemctl start neonvibe` levanta la app en el puerto configurado.
2. Acceso desde WAN vía dominio + HTTPS (Cloudflare).
3. El restart no pierde datos ni sesiones activas (graceful shutdown + Flyway idempotente).
4. `deploy.sh` funciona sin intervención manual más allá de los secrets.
5. En prod, el usuario puede loguearse con Google (LoginPage) y usar la app.
6. `./mvnw test` y `pnpm build` siguen verdes.

---

## 7. Riesgos y Mitigaciones

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Falta `JWT_SECRET`/`DB_PASSWORD` en prod | Arranque falla | `application-prod.yml` sin defaults; el service carga `neonvibe.env` |
| Cloudflare Flexible sin WS | Sync no funciona | WebSocket soportado; documentar `wss://` |
| Permisos de `/srv/Music` | Scanner falla | 755 legible; usuario `neonvibe` |
| Flyway en DB con datos previos | Migración falla | V1-V7 idempotentes; backup antes del primer deploy |
| Frontend sin Google Client ID en prod | Login imposible | `VITE_GOOGLE_CLIENT_ID` en build; LoginPage avisa si falta |

---

*Documento de especificación — Fase 11. Fecha: 2026-08-06.*
