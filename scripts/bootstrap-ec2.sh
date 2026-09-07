#!/usr/bin/env bash
set -euo pipefail

if command -v dnf >/dev/null 2>&1; then
  sudo dnf update -y
  sudo dnf install -y docker
  sudo systemctl enable --now docker
  sudo usermod -aG docker "$USER"

  if ! docker compose version >/dev/null 2>&1; then
    echo "Docker Compose plugin no está disponible. Instalando plugin manualmente..."
    DOCKER_CONFIG=${DOCKER_CONFIG:-$HOME/.docker}
    mkdir -p "$DOCKER_CONFIG/cli-plugins"
    curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
      -o "$DOCKER_CONFIG/cli-plugins/docker-compose"
    chmod +x "$DOCKER_CONFIG/cli-plugins/docker-compose"
  fi
elif command -v apt-get >/dev/null 2>&1; then
  sudo apt-get update
  sudo apt-get install -y docker.io docker-compose-v2
  sudo systemctl enable --now docker
  sudo usermod -aG docker "$USER"
else
  echo "Distribución no soportada automáticamente. Instala Docker y Docker Compose manualmente."
  exit 1
fi

echo
echo "Docker instalado. Cierra la sesión SSH y vuelve a entrar para aplicar el grupo docker."
docker --version || true
docker compose version || true
