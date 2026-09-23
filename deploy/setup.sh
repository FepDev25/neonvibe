#!/usr/bin/env bash
#
# setup.sh — Preparación inicial del servidor Debian Trixie (ejecutar UNA vez, como root).
#
# Instala dependencias, crea el usuario de servicio, directorios, base de datos
# PostgreSQL, la unit de systemd y el archivo de secrets (con JWT_SECRET y
# DB_PASSWORD generados automáticamente).
#
# Uso:
#   sudo ./deploy/setup.sh
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

SERVICE_USER="neonvibe"
INSTALL_DIR="/opt/neonvibe"
DATA_DIR="/var/lib/neonvibe"
ENV_FILE="$INSTALL_DIR/neonvibe.env"
SERVICE_UNIT="$ROOT/deploy/neonvibe.service"
DB_NAME="neonvibe"
DB_USER="neonvibe"

if [ "$(id -u)" -ne 0 ]; then
  echo "ERROR: ejecuta con sudo (root)." >&2
  exit 1
fi

echo "==> [1/5] Dependencias (Java 21, PostgreSQL, curl)"
export DEBIAN_FRONTEND=noninteractive
if ! command -v java >/dev/null 2>&1; then
  apt-get update -q
  apt-get install -y -q openjdk-21-jre-headless curl openssl
fi
if ! command -v psql >/dev/null 2>&1; then
  apt-get install -y -q postgresql postgresql-contrib
fi

echo "==> [2/5] Usuario y directorios"
id -u "$SERVICE_USER" >/dev/null 2>&1 || useradd --system --no-create-home --shell /usr/sbin/nologin "$SERVICE_USER"
# /opt/neonvibe es de root (el servicio solo lee el JAR y el env; systemd los carga).
install -d -m 0755 -o root -g root "$INSTALL_DIR"
# /var/lib/neonvibe es donde el servicio ESCRIBE (covers/lyrics).
install -d -m 0755 -o "$SERVICE_USER" -g "$SERVICE_USER" "$DATA_DIR/covers" "$DATA_DIR/lyrics"

echo "==> [3/5] PostgreSQL (base + usuario)"
DB_PASS="$(openssl rand -hex 16)"
if ! sudo -u postgres psql -tAc "SELECT 1 FROM pg_roles WHERE rolname='$DB_USER'" | grep -q 1; then
  sudo -u postgres psql -c "CREATE USER $DB_USER WITH PASSWORD '$DB_PASS';"
  echo "  Usuario '$DB_USER' creado (password generada)."
else
  echo "  Usuario '$DB_USER' ya existe (no se toca la password)."
fi
if ! sudo -u postgres psql -tAc "SELECT 1 FROM pg_database WHERE datname='$DB_NAME'" | grep -q 1; then
  sudo -u postgres psql -c "CREATE DATABASE $DB_NAME OWNER $DB_USER;"
  echo "  Base de datos '$DB_NAME' creada."
fi

echo "==> [4/5] systemd"
[ -f "$SERVICE_UNIT" ] || { echo "ERROR: no existe $SERVICE_UNIT" >&2; exit 1; }
install -m 0644 "$SERVICE_UNIT" /etc/systemd/system/neonvibe.service
systemctl daemon-reload
# enable (sin --now: aún no hay JAR desplegado) para que arranque tras un reboot.
systemctl enable neonvibe.service >/dev/null 2>&1
echo "  Unit instalada y habilitada al boot: /etc/systemd/system/neonvibe.service"

echo "==> [5/5] Secrets"
if [ ! -f "$ENV_FILE" ]; then
  JWT_SECRET="$(openssl rand -base64 32)"
  # Plantilla con los secrets REALES generados (evita arrancar con CHANGE_ME).
  {
    echo "# NeonVibe — producción (generado por setup.sh)"
    echo "JWT_SECRET=$JWT_SECRET"
    echo "DB_URL=jdbc:postgresql://localhost:5432/$DB_NAME"
    echo "DB_USERNAME=$DB_USER"
    echo "DB_PASSWORD=$DB_PASS"
    echo ""
    echo "# --- Dominio / orígenes (EDITAR) ---"
    echo "CORS_ALLOWED_ORIGINS=https://tudominio.com"
    echo "WS_ORIGINS=https://tudominio.com"
    echo "FRONTEND_BASE=https://tudominio.com"
    echo ""
    echo "# --- Login Google (EDITAR — la app NO arranca sin esto) ---"
    echo "# Client ID de Google Cloud Console (OAuth 2.0 -> Web application)."
    echo "# Se verifica contra el claim 'aud' del id_token."
    echo "GOOGLE_CLIENT_ID=CHANGE_ME.apps.googleusercontent.com"
    echo "# Allowlist de cuentas autorizadas (separadas por coma). SIN esto,"
    echo "# cualquier persona con cuenta de Google entraría a tu biblioteca."
    echo "ALLOWED_EMAILS=CHANGE_ME@gmail.com"
    echo ""
    echo "# --- Servidor ---"
    echo "SERVER_PORT=8080"
    echo "MUSIC_PATHS=/srv/Music"
    echo "SCAN_INTERVAL=0"
    echo "COVERS_CACHE=/var/lib/neonvibe/covers"
    echo "LYRICS_CACHE=/var/lib/neonvibe/lyrics"
    echo ""
    echo "# --- Opcionales ---"
    echo "# LASTFM_API_KEY="
    echo "# LASTFM_API_SECRET="
  } > "$ENV_FILE"
  # systemd lee el env como root ANTES de bajar privilegios; 0640 root:neonvibe.
  chown root:"$SERVICE_USER" "$ENV_FILE"
  chmod 0640 "$ENV_FILE"
  echo "  Secrets creados: $ENV_FILE (JWT_SECRET y DB_PASSWORD ya generados)"
  echo "  >> EDITAR LAS VARIABLES DE DOMINIO (CORS_ALLOWED_ORIGINS, WS_ORIGINS, FRONTEND_BASE)"
else
  echo "  $ENV_FILE ya existe (no se sobreescribe)."
fi

echo ""
echo "==> Música"
if [ -d /srv/Music ]; then
  echo "  /srv/Music presente. Asegúrate de que '$SERVICE_USER' puede LEERLO:"
  echo "    chmod o+x /srv && chmod o+rx /srv/Music   (o añade neonvibe al grupo felipep y usa ACLs)"
else
  echo "  /srv/Music no existe. Crea el path o ajusta MUSIC_PATHS en $ENV_FILE."
fi

echo ""
echo "=== LISTO ==="
echo "1) Edita las variables de dominio en $ENV_FILE"
echo "2) Genera un JAR:  ./deploy/build.sh   (o copia dist/neonvibe.jar desde tu máquina)"
echo "3) Despliega:       ./deploy/deploy.sh"
