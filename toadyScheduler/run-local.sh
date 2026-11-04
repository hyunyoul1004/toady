#!/usr/bin/env bash
set -euo pipefail

java_major_version() {
	"$1" -version 2>&1 | awk -F '"' '/version/ { split($2, parts, "."); print (parts[1] == 1 ? parts[2] : parts[1]); exit }'
}

java_command="$(command -v java || true)"
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
	java_command="$JAVA_HOME/bin/java"
fi

if [[ -n "${JAVA_HOME:-}" ]]; then
	export PATH="$JAVA_HOME/bin:$PATH"
fi

java_version=""
if [[ -n "$java_command" ]]; then
	java_version="$(java_major_version "$java_command")"
fi

if [[ "$java_version" != "21" ]]; then
	echo "Java 21 is required. Set JAVA_HOME or add a Java 21 installation to PATH." >&2
	exit 1
fi

if [[ ! -f .env.local ]]; then
	echo "Missing .env.local. Add local database and API credentials first." >&2
	exit 1
fi

set -a
source .env.local
set +a

if (($# == 0)); then
	exec ./gradlew bootRun
fi

exec ./gradlew "$@"
