#!/usr/bin/env bash
#
# install-ytdlp.sh — Instala/actualiza yt-dlp y Deno para la importación desde
# YouTube (ejecutar como root, una vez y cuando YouTube cambie).
#
#   sudo ./deploy/install-ytdlp.sh
#
# yt-dlp >= 2025.11.12 necesita un runtime JavaScript externo para YouTube.
# Se usa el binario oficial (incluye yt-dlp-ejs) y Deno (runtime recomendado).
# Actualización posterior: `sudo yt-dlp -U`.
#
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "ERROR: ejecuta con sudo (root)." >&2
  exit 1
fi

echo "==> Comprobando dependencias (curl, unzip, ffmpeg)"
export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get install -y -q curl unzip ffmpeg

ARCH="$(uname -m)"
case "$ARCH" in
  x86_64)  YTDLP_ASSET="yt-dlp_linux"; DENO_ASSET="deno-x86_64-unknown-linux-gnu.zip" ;;
  aarch64) YTDLP_ASSET="yt-dlp_linux_aarch64"; DENO_ASSET="deno-aarch64-unknown-linux-gnu.zip" ;;
  *)
    echo "ERROR: arquitectura no soportada: $ARCH" >&2
    exit 1
    ;;
esac

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "==> Instalando yt-dlp ($ARCH)"
curl -fsSL "https://github.com/yt-dlp/yt-dlp/releases/latest/download/$YTDLP_ASSET" \
  -o "$TMP/yt-dlp"
install -m 0755 "$TMP/yt-dlp" /usr/local/bin/yt-dlp

echo "==> Instalando Deno ($ARCH)"
curl -fsSL "https://github.com/denoland/deno/releases/latest/download/$DENO_ASSET" \
  -o "$TMP/deno.zip"
unzip -q -o "$TMP/deno.zip" -d "$TMP/deno"
install -m 0755 "$TMP/deno/deno" /usr/local/bin/deno

echo "==> Preparando carpeta temporal de descargas"
if id -u neonvibe >/dev/null 2>&1; then
  install -d -m 0755 -o neonvibe -g neonvibe /var/lib/neonvibe/ytdlp \
    /var/lib/neonvibe/ytdlp/deno
  echo "  /var/lib/neonvibe/ytdlp{,/deno} (neonvibe:neonvibe) OK"
else
  echo "  AVISO: el usuario 'neonvibe' no existe; crea /var/lib/neonvibe/ytdlp a mano."
fi

echo "==> Versiones instaladas"
/usr/local/bin/yt-dlp --version
/usr/local/bin/deno --version | head -1
command -v ffmpeg >/dev/null && ffmpeg -version | head -1

echo
echo "Listo. Comprueba que el usuario del servicio los ve:"
echo "  sudo -u neonvibe env -i PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin sh -c 'yt-dlp --version; deno --version | head -1'"
echo
echo "Para actualizar yt-dlp más adelante:  sudo yt-dlp -U"
