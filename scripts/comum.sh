#!/usr/bin/env sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
if [ "$(uname -s)" = "Darwin" ] && [ -x /usr/libexec/java_home ]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null) || { echo 'Instale o JDK 21.' >&2; exit 1; }
  export JAVA_HOME
  PATH="$JAVA_HOME/bin:$PATH"
  export PATH
fi
if ! java -version 2>&1 | head -n 1 | grep -q '"21\.'; then
  echo 'Este projeto usa JDK 21. Configure JAVA_HOME para o JDK 21.' >&2
  exit 1
fi
