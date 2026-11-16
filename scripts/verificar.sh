#!/usr/bin/env sh
set -eu
. "$(dirname "$0")/comum.sh"
./mvnw -B verify
git diff --check
