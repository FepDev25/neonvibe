#!/usr/bin/env bash
#
# build.sh — Build del JAR de producción con el frontend embebido.
#
# Puede ejecutarse en la máquina de desarrollo o en el servidor (si tiene
# Node/pnpm y Maven). Produce: dist/neonvibe.jar
#
# Uso:
#   ./deploy/build.sh
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

JAR_SRC="backend/target/neonvibe-backend-0.1.0-SNAPSHOT.jar"
DIST="$ROOT/dist"

echo "==> [1/3] Frontend (pnpm)"
if ! command -v pnpm >/dev/null 2>&1; then
  echo "ERROR: pnpm no está instalado (Node 20+ requerido)." >&2
  exit 1
fi
# El Client ID se hornea en el bundle en tiempo de build. Sin él, el JAR
# resultante muestra "Falta VITE_GOOGLE_CLIENT_ID" y NADIE puede iniciar sesión
# — y no te enterarías hasta abrir el navegador en producción.
if [ -z "${VITE_GOOGLE_CLIENT_ID:-}" ]; then
  echo "ERROR: falta la variable VITE_GOOGLE_CLIENT_ID." >&2
  echo "       El login de Google no funcionaría en el JAR generado." >&2
  echo "" >&2
  echo "  VITE_GOOGLE_CLIENT_ID=\"xxxx.apps.googleusercontent.com\" ./deploy/build.sh" >&2
  echo "" >&2
  echo "       (debe ser el MISMO valor que GOOGLE_CLIENT_ID en neonvibe.env)" >&2
  echo "       Para un build sin login (solo pruebas): ALLOW_NO_GOOGLE=1 ./deploy/build.sh" >&2
  [ "${ALLOW_NO_GOOGLE:-}" = "1" ] || exit 1
  echo "AVISO: ALLOW_NO_GOOGLE=1 — build sin login de Google." >&2
fi
# pnpm install needs devDependencies (vite, typescript) so NODE_ENV must NOT be
# 'production' there; but `vite build` must run with NODE_ENV=production or Vite
# will not statically inline import.meta.env.VITE_* vars (they resolve to undefined
# at runtime). Set each explicitly so the build is correct regardless of the
# caller's environment.
(cd frontend && NODE_ENV=development pnpm install --frozen-lockfile && NODE_ENV=production pnpm build)

echo "==> [2/3] Backend (Maven)"
if ! command -v java >/dev/null 2>&1 || ! command -v mvn >/dev/null 2>&1; then
  # El wrapper de Maven necesita un JDK presente; si no hay mvn global, usa ./mvnw.
  command -v java >/dev/null 2>&1 || { echo "ERROR: se requiere Java 21." >&2; exit 1; }
fi
if command -v mvn >/dev/null 2>&1; then
  (cd backend && mvn -q clean package -DskipTests)
else
  (cd backend && ./mvnw -q clean package -DskipTests)
fi

[ -f "$JAR_SRC" ] || { echo "ERROR: no se generó el JAR: $JAR_SRC" >&2; exit 1; }

echo "==> [3/3] Copiando a dist/neonvibe.jar"
mkdir -p "$DIST"
cp "$JAR_SRC" "$DIST/neonvibe.jar"
echo ""
echo "OK: $DIST/neonvibe.jar"
echo "    sha256: $(sha256sum "$DIST/neonvibe.jar" | cut -d' ' -f1)"
