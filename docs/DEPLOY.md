# Guía de Despliegue — NeonVibe

> Despliega NeonVibe en tu servidor Debian Trixie (bare-metal) como un único JAR servido por systemd, detrás de Cloudflare. El frontend va embebido en el JAR.

**Estado (2026-08-12):** NeonVibe está desplegado en `https://neonvibe.fepdev.app`.
Esta guía describe el proceso de despliegue y operación tal como se ejecutó.

---

## 1. Resumen

```
Internet ── HTTPS (Cloudflare) ──► Debian Trixie :8080 (Spring Boot, frontend embebido)
                                    ├── systemd service `neonvibe` (user neonvibe)
                                    ├── /opt/neonvibe/neonvibe.jar + neonvibe.env
                                    ├── /srv/Music        (música, solo lectura)
                                    ├── /var/lib/neonvibe/covers + /lyrics (cache)
                                    └── PostgreSQL (db/user `neonvibe`)
```

- **Un solo proceso**: Spring Boot sirve la API (`/api/v1/**`), el frontend
  estático (`classpath:/static`) y el WebSocket (`/ws`) en el puerto 8080.
- **No se usa Nginx**: Cloudflare termina TLS y reenvía a `http://<ip>:8080`.
- WebSocket atraviesa Cloudflare (`wss://<dominio>/ws`).

---

## 2. Requisitos

### Servidor (Debian Trixie)
- OpenJDK 21 (JRE) — instalado por `deploy/setup.sh`.
- PostgreSQL 15+ (nativo) — instalado por `deploy/setup.sh`.
- `systemd`, `curl`.
- Directorio de música legible por el usuario de servicio (por defecto `/srv/Music`).

### Máquina de desarrollo (esta PC)
- Java 21, Maven (o el wrapper), Node 20+ y pnpm — para generar el JAR.

### Cuentas / servicios
- Dominio + zona DNS en **Cloudflare**.
- Proyecto en **Google Cloud Console** (Client ID OAuth web) para el login.
- (Opcional) API account en **Last.fm** para scrobbling.

---

## 3. Preparar el login de Google (una vez)

1. Ve a [Google Cloud Console](https://console.cloud.google.com) → crea un proyecto.
2. **APIs & Services → OAuth consent screen** (External, publicar).
   - Authorized JavaScript origins: `https://tudominio.com`
3. **Credentials → Create credentials → OAuth client ID → Web application**.
   - Authorized JavaScript origins: `https://tudominio.com`
   - (No hace falta redirect URI: la app usa Google Identity Services y valida el
     `id_token` en el backend vía `tokeninfo`.)
4. Copia el **Client ID**. Lo necesitas en **dos** sitios y deben coincidir:
   - `VITE_GOOGLE_CLIENT_ID` → en el build del frontend (sección 4).
   - `GOOGLE_CLIENT_ID` → en `neonvibe.env` (el backend verifica con él el claim
     `aud` del `id_token`).

> **Por qué el backend también lo necesita:** `tokeninfo` confirma que el token
> es auténtico, pero no que se emitiera *para tu app*. Sin verificar `aud`,
> cualquiera podría tomar su `id_token` de otra web con Google Sign-In y usarlo
> para entrar aquí. La app **no arranca** en prod si falta `GOOGLE_CLIENT_ID`.

### Allowlist de cuentas (obligatorio)

Google autentica a *cualquiera* con una cuenta. Para que tu biblioteca no quede
abierta al mundo, `ALLOWED_EMAILS` define quién puede registrarse:

```
ALLOWED_EMAILS=tu-correo@gmail.com,otra-persona@gmail.com
```

La app **no arranca** en prod si está vacío. Una cuenta fuera de la lista recibe
403 y ve "Esta cuenta no está autorizada en este servidor".

---

## 4. Generar el JAR (máquina de desarrollo o servidor)

```bash
# En la raíz del repo:
./deploy/build.sh
```

Hace tres cosas:

1. `pnpm install --frozen-lockfile && pnpm build` → genera el frontend en
   `backend/src/main/resources/static`.
2. `mvn clean package` → empaqueta el JAR con el frontend dentro.
3. Copia `backend/target/neonvibe-backend-0.1.0-SNAPSHOT.jar` a `dist/neonvibe.jar`
   y muestra su `sha256`.

El Client ID se hornea en el bundle en tiempo de build, así que `build.sh` lo
**exige** y aborta si falta (antes generaba un JAR en el que nadie podía entrar):

```bash
VITE_GOOGLE_CLIENT_ID="xxxx.apps.googleusercontent.com" ./deploy/build.sh
```

Debe ser el **mismo** valor que `GOOGLE_CLIENT_ID` en `neonvibe.env`. Para un
build de prueba sin login: `ALLOW_NO_GOOGLE=1 ./deploy/build.sh`.

---

## 5. Preparar el servidor (una vez)

Copia el repo (o solo `deploy/`) al servidor y ejecuta:

```bash
sudo ./deploy/setup.sh
```

Hace:

- Instala **OpenJDK 21 JRE** + **PostgreSQL** + curl (si faltan).
- Crea el usuario de sistema **`neonvibe`** (sin shell, sin home).
- Crea `/opt/neonvibe` y `/var/lib/neonvibe/{covers,lyrics}` (propiedad neonvibe).
- Crea la base y el usuario **PostgreSQL** `neonvibe` (si no existen).
- Instala `/etc/systemd/system/neonvibe.service`.
- Copia `neonvibe.env.example` → `/opt/neonvibe/neonvibe.env` (si no existe).

### Permisos de la música

`/srv/Music` debe ser legible por `neonvibe`. Opciones:

```bash
# Opción simple (mundo-legible):
chmod o+x /srv && chmod o+rx /srv/Music

# Opción con grupo:
usermod -aG felipep neonvibe
# y asegura permisos de grupo en /srv/Music (g+rX).
```

---

## 6. Secrets — `/opt/neonvibe/neonvibe.env`

Edita el archivo (propiedad `neonvibe:neonvibe`, modo `0600`):

```bash
sudo nano /opt/neonvibe/neonvibe.env
```

Variables **obligatorias**:

| Variable | Valor | Notas |
|---|---|---|
| `JWT_SECRET` | `openssl rand -base64 32` | Secreto HS256 |
| `DB_PASSWORD` | password de PostgreSQL | El user es `neonvibe` por defecto |
| `CORS_ALLOWED_ORIGINS` | `https://tudominio.com` | Lista separada por comas |
| `WS_ORIGINS` | `https://tudominio.com` | Patrón único para WebSocket |
| `FRONTEND_BASE` | `https://tudominio.com` | URL pública (callbacks) |
| `GOOGLE_CLIENT_ID` | `xxxx.apps.googleusercontent.com` | Verifica el `aud` del id_token. Igual que `VITE_GOOGLE_CLIENT_ID` |
| `ALLOWED_EMAILS` | `tu-correo@gmail.com` | Allowlist separada por comas. Sin ella, cualquier cuenta de Google entraría |

Opcionales: `LASTFM_API_KEY`, `LASTFM_API_SECRET`.

`setup.sh` deja `GOOGLE_CLIENT_ID` y `ALLOWED_EMAILS` con `CHANGE_ME`: **debes
editarlas** o el servicio no arranca (falla rápido con el motivo en el journal).

> El JWT usa un **secret único** por usuario; escribe `JWT_SECRET` con cuidado
> porque invalidarás las sesiones si lo cambias.

---

## 7. Cloudflare (DNS + SSL)

1. En Cloudflare → **DNS → Records**, crea:
   - Tipo `A`, nombre `@`, contenido `<IP-del-servidor>`, **proxy activado** (nube naranja).
   - Tipo `A`, nombre `www`, contenido `<IP-del-servidor>`, proxy activado.
2. **SSL/TLS → Overview**: modo **Full (strict)** con un certificado de origen de
   Cloudflare (gratuito): crea el cert en *SSL/TLS → Origin Server → Create
   Certificate*, instálalo en el servidor (por ejemplo en
   `/etc/ssl/cloudflare.crt` + `cloudflare.key`) y configura el JAR para usar
   HTTPS en 8080 (ver nota abajo). Como alternativa rápida para empezar,
   **Flexible** deja el tráfico en claro entre Cloudflare y tu servidor (aceptable
   en una LAN de confianza, pero no recomendado en Internet).
3. WebSocket está habilitado por defecto en todos los planes (no hace nada especial).
4. Espera a que el DNS propague (minutos).

> Si usas **Full (strict)** y terminas TLS en el propio JAR, sirve HTTPS con:
> `--server.ssl.enabled=true --server.ssl.certificate=/etc/ssl/cloudflare.crt
> --server.ssl.certificate-private-key=/etc/ssl/cloudflare.key` (variables en el
> `.env`). Con **Flexible**, deja el JAR en HTTP (puerto 8080) y Cloudflare hace
> el resto.

---

## 8. Desplegar

En el servidor, con el JAR generado:

```bash
# Copia el JAR al servidor (desde tu máquina):
scp dist/neonvibe.jar usuario@servidor:/tmp/neonvibe.jar

# En el servidor:
sudo ./deploy/deploy.sh /tmp/neonvibe.jar
# o sin argumento si ya está en dist/neonvibe.jar
```

El script:
1. Copia el JAR a `/opt/neonvibe/neonvibe.jar` (con `mv` atómico).
2. `systemctl daemon-reload && systemctl restart neonvibe`.
3. Espera hasta 60s a que `GET /actuator/health` responda `UP`.
4. Si falla, muestra el log (`journalctl -u neonvibe -n 50`).

### Primer arranque

- **Flyway** aplica las migraciones `V1..V7` automáticamente. Si la base ya tenía
  datos de una versión anterior, haz backup antes del primer deploy.
- **Escaneo**: dispara el primer scan manual:

  ```bash
  curl -X POST http://localhost:8080/api/v1/admin/scan -H "Authorization: Bearer <token>"
  # estado: curl http://localhost:8080/api/v1/admin/scan/status -H "Authorization: Bearer <token>"
  ```
  (O configura `SCAN_INTERVAL` en segundos en el `.env` para escaneo periódico.)

---

## 9. Verificación

```bash
# Salud
curl http://localhost:8080/actuator/health          # {"status":"UP"}

# Desde Internet
curl -I https://tudominio.com                        # 200, HTML del frontend

# Servicio
systemctl status neonvibe
journalctl -u neonvibe -f                            # logs en vivo
```

### Comprobar el login (navegador)
1. Abre `https://tudominio.com` → verás la pantalla de login de Google.
2. Inicia sesión → la app carga la biblioteca.
3. Reproduce una canción → el reproductor hace streaming con seek.
4. Dos pestañas → el sync WebSocket mantiene el estado del reproductor.

---

## 10. Operación

```bash
sudo systemctl start|stop|restart neonvibe
sudo systemctl enable neonvibe          # arranque al boot (ya lo hace setup.sh)
journalctl -u neonvibe -f               # logs
```

### Backup (recomendado)
```bash
# Base de datos
sudo -u postgres pg_dump neonvibe > neonvibe-$(date +%F).sql
# Cache (recreable; opcional respaldarla)
sudo tar czf neonvibe-cache-$(date +%F).tgz /var/lib/neonvibe
```

### Rollback
```bash
# Guarda la versión anterior del JAR antes de desplegar:
sudo cp /opt/neonvibe/neonvibe.jar /opt/neonvibe/neonvibe.jar.bak
# Para revertir:
sudo cp /opt/neonvibe/neonvibe.jar.bak /opt/neonvibe/neonvibe.jar
sudo systemctl restart neonvibe
```
Las migraciones de Flyway son aditivas y hacia delante: si vuelves a un JAR más
antiguo, las migraciones nuevas ya aplicadas se ignoran (no se revierten).

---

## 11. Troubleshooting

| Síntoma | Causa probable | Solución |
|---|---|---|
| Servicio en `failed` | Falta `JWT_SECRET`/`DB_PASSWORD` | Completa `/opt/neonvibe/neonvibe.env` |
| `401` en todo | `JWT_SECRET` cambió | Conserva el mismo secret o vuelve a loguearte |
| Health UP pero no abre el frontend | CORS/dominio | Revisa `CORS_ALLOWED_ORIGINS` y `WS_ORIGINS` |
| Login de Google no aparece | Falta `VITE_GOOGLE_CLIENT_ID` en el build | Rebuild con la variable (sección 4) |
| `failed` con "GOOGLE_CLIENT_ID is not configured" | Sigue en `CHANGE_ME` | Edita `neonvibe.env` (sección 3) |
| `failed` con "ALLOWED_EMAILS is empty" | Sin allowlist | Añade tu correo a `ALLOWED_EMAILS` |
| `403` "cuenta no autorizada" al entrar | Ese correo no está en `ALLOWED_EMAILS` | Añádelo y `sudo systemctl restart neonvibe` |
| `401` "not issued for this application" | `GOOGLE_CLIENT_ID` ≠ `VITE_GOOGLE_CLIENT_ID` | Deben ser idénticos; rebuild o corrige el env |
| Scanner no ve música | Permisos | Verifica `/srv/Music` legible por `neonvibe` (sección 5) |
| WebSocket no conecta | WS_ORIGINS / proxy | Revisa `WS_ORIGINS` y que Cloudflare tenga WS habilitado |
| WebSocket `405` en `GET /ws/info` | SPA catch-all sombreaba el endpoint WS | Ya resuelto en código: el fallback SPA excluye `/api`, `/ws` y `/actuator`. Reconstruye y redespliega |
| Flyway falla al migrar | DB con datos previos | Haz backup y luego `sudo -u postgres psql neonvibe -c "INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success) VALUES (0, NULL, '<< Flyway Baseline >>', 'BASELINE', '<Flyway Baseline>', NULL, 'postgres', 0, true);"` (o `flyway baseline` si tienes el CLI) |
| Letras/carátulas vacías | Sin internet en servidor | Revisa DNS/salida; el placeholder neón cubre |

---

## 12. Seguridad (notas)

- `neonvibe.env` es `0600` y nunca se commitea (solo existe `neonvibe.env.example`).
- El JWT viaja en query param solo para media (`/stream`, `/cover`); evita exponerlo
  en logs (Cloudflare Flexible loguea la ruta, no el query si configuras redaction).
- Recomendado: restringir firewall a los puertos 80/443 (Cloudflare) y 5432 solo a localhost.
- Actualiza el JAR periódicamente (`deploy/deploy.sh`) y revisa `journalctl` tras cada deploy.

---

*Guía de despliegue — NeonVibe. Fecha: 2026-08-12.*
