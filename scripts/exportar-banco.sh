#!/usr/bin/env sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
tempfile=$(mktemp)
trap 'rm -f "$tempfile"' EXIT HUP INT TERM
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --schema-only --no-owner --no-privileges --exclude-table=flyway_schema_history' > "$tempfile"
test -s "$tempfile"
mv "$tempfile" database/schema.sql
echo 'Estrutura exportada sem dados para database/schema.sql. Revise antes de commit.'
