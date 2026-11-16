#!/usr/bin/env sh
set -eu
. "$(dirname "$0")/comum.sh"
docker compose up -d --wait db
exec ./mvnw spring-boot:run
