#!/usr/bin/env sh
set -eu
. "$(dirname "$0")/comum.sh"
if [ ! -f .env ]; then
  password=$(openssl rand -hex 24)
  sed "s/troque-por-uma-senha-local/$password/" .env.example > .env
  chmod 600 .env
  echo 'Configuração local criada em .env (não versionada).'
fi
docker compose up -d --wait db
./mvnw -B verify
echo 'Ambiente verificado. Para iniciar: ./scripts/iniciar.sh'
