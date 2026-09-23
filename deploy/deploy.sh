#!/usr/bin/env bash
#
# deploy.sh — Despliegue repetible en el servidor.
#
# Copia el JAR a /opt/neonvibe/neonvibe.jar, reinicia el servicio systemd y
# espera a que el health check responda UP.
#
# Uso (en el servidor):
#   ./deploy/deploy.sh                 # usa dist/neonvibe.jar
#   ./deploy/deploy.sh /tmp/neonvibe.jar
#
# Requiere: sudo (systemctl) y /opt/neonvibe/neonvibe.env ya configurado.
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="${1:-$ROOT/dist/neonvibe.jar}"

INSTALL_DIR="/opt/neonvibe"
ENV_FILE="$INSTALL_DIR/neonvibe.env"
SERVICE="neonvibe"
HEALTH_URL="http://localhost:8080/actuator/health"

if [ "$(id -u)" -ne 0 ]; then
  echo "ERROR: ejecuta con sudo (necesita systemctl y escribir en $INSTALL_DIR)." >&2
  exit 1
fi
if [ ! -f "$JAR" ]; then
  echo "ERROR: no existe el JAR: $JAR  (genera uno con ./deploy/build.sh)" >&2
  exit 1
fi
if [ ! -f "$ENV_FILE" ]; then
  echo "ERROR: falta $ENV_FILE (crea uno con ./deploy/setup.sh o copia deploy/neonvibe.env.example)" >&2
  exit 1
fi

# Puerto real desde el env (por si SERVER_PORT cambió).
if grep -q '^SERVER_PORT=' "$ENV_FILE"; then
  PORT="$(grep '^SERVER_PORT=' "$ENV_FILE" | cut -d= -f2)"
  [ -n "$PORT" ] && HEALTH_URL="http://localhost:$PORT/actuator/health"
fi

echo "==> Copiando JAR ($(basename "$JAR"), $(du -h "$JAR" | cut -f1))"
# Backup de la versión actual para poder hacer rollback.
if [ -f "$INSTALL_DIR/neonvibe.jar" ]; then
  cp "$INSTALL_DIR/neonvibe.jar" "$INSTALL_DIR/neonvibe.jar.prev"
fi
install -m 0644 -o root -g root "$JAR" "$INSTALL_DIR/neonvibe.jar.new"
mv "$INSTALL_DIR/neonvibe.jar.new" "$INSTALL_DIR/neonvibe.jar"

echo "==> Reiniciando servicio"
systemctl daemon-reload
systemctl restart "$SERVICE"

echo "==> Esperando health check"
for i in $(seq 1 30); do
  if curl -fsS -o /dev/null "$HEALTH_URL" 2>/dev/null; then
    echo "OK: salud UP tras ${i} intentos ($HEALTH_URL)"
    systemctl --no-pager --lines=0 status "$SERVICE"
    exit 0
  fi
  sleep 2
done

echo "ERROR: el servicio no respondió. Revisa:" >&2
journalctl -u "$SERVICE" -n 50 --no-pager >&2
exit 1
