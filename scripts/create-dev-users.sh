#!/usr/bin/env bash
# 개발 DB에 테스트 계정(devNNNN@example.test)을 만든다. 저장소 루트에서 실행한다.
#   scripts/create-dev-users.sh          # 1000개
#   scripts/create-dev-users.sh 200      # 200개
# 접속 정보는 toadyWebApi/.env의 DB_URL·DB_USERNAME·DB_PASSWORD를 쓰고, 비밀번호는 입력받는다(화면에 보이지 않음).
set -euo pipefail
cd "$(dirname "$0")/.."

count="${1:-1000}"
env_file="toadyWebApi/.env"
[[ -f "$env_file" ]] || { echo "$env_file 이 없습니다."; exit 1; }

# 운영 DB에 넣지 않도록 접속할 호스트를 보여 주고 확인받는다.
host="$(grep -E '^DB_URL=' "$env_file" | head -1 | sed -E 's#^DB_URL=["'"'"']?jdbc:[a-z]+://([^/?"'"'"']+).*#\1#')"
echo "대상 DB 호스트: $host"
read -r -p "개발 DB가 맞으면 yes를 입력하세요: " answer
[[ "$answer" == "yes" ]] || { echo "취소했습니다."; exit 1; }

# MariaDB 드라이버와 BCrypt는 API 빌드가 받아 둔 Gradle 캐시에서 찾는다(먼저 toadyWebApi에서 ./gradlew test 한 번).
find_jar() { find "$HOME/.gradle/caches/modules-2/files-2.1" -name "$1-*.jar" ! -name '*sources*' 2>/dev/null | sort -V | tail -1; }
cp_list="$(find_jar mariadb-java-client):$(find_jar spring-security-crypto):$(find_jar spring-jcl)"
[[ "$cp_list" != *"::"* && "$cp_list" != :* && "$cp_list" != *: ]] || { echo "라이브러리를 찾지 못했습니다. toadyWebApi에서 ./gradlew test 를 먼저 실행하세요."; exit 1; }

java_cmd="${JAVA_HOME:+$JAVA_HOME/bin/}java"
read -r -s -p "테스트 계정 비밀번호: " password; echo
printf '%s\n' "$password" | "$java_cmd" -cp "$cp_list" scripts/CreateDevUsers.java "$count" "$env_file"
