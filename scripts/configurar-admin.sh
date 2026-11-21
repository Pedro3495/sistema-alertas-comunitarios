#!/usr/bin/env sh
set -eu
. "$(dirname "$0")/comum.sh"
if [ "$#" -ne 1 ]; then
  echo 'Uso: ./scripts/configurar-admin.sh criar|recuperar' >&2
  exit 1
fi
case "$1" in
  criar|recuperar) acao_admin="$1" ;;
  *) echo 'Use criar ou recuperar; nunca informe senha como argumento.' >&2; exit 1 ;;
esac
if [ ! -t 0 ] || [ ! -t 1 ]; then
  echo 'Execute em terminal interativo; não redirecione a senha.' >&2
  exit 1
fi
# Recompila antes do comando para não executar uma versão antiga do provisionamento.
./mvnw -q -DskipTests package
exec java -jar target/alertas-comunitarios-0.0.1-SNAPSHOT.jar \
  --spring.main.web-application-type=none --alertas.admin="$acao_admin"
