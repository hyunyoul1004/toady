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

# 값을 셸 명령으로 실행(source)하지 않고 글자 그대로 읽는다. DB_URL의 '&'(접속 옵션 구분자)나 비밀번호의 '#'·'!'가
# 셸 문법으로 해석되면 값이 잘리거나 비기 때문이다. 빈 줄·# 주석은 건너뛰고, 값 양끝의 따옴표 한 쌍은 벗긴다.
while IFS= read -r line || [[ -n "$line" ]]; do
	line="${line%$'\r'}"
	[[ -z "$line" || "$line" == \#* || "$line" != *=* ]] && continue
	key="${line%%=*}"
	value="${line#*=}"
	[[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
	if [[ ${#value} -ge 2 && ( ( "${value:0:1}" == '"' && "${value: -1}" == '"' ) || ( "${value:0:1}" == "'" && "${value: -1}" == "'" ) ) ]]; then
		value="${value:1:${#value}-2}"
	fi
	export "$key=$value"
done < .env.local

if (($# == 0)); then
	exec ./gradlew bootRun
fi

exec ./gradlew "$@"
