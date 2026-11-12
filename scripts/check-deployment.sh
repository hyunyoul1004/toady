#!/usr/bin/env bash
set -eu

# API readiness includes database connectivity. Bound both retries and request time.
ready=false
for attempt in $(seq 1 30); do
  if curl --fail --silent --max-time 3 http://127.0.0.1:8080/internal/health >/dev/null; then
    ready=true
    break
  fi
  sleep 2
done
if [ "$ready" != true ]; then
  echo 'API readiness check failed' >&2
  exit 1
fi

# JVM 실행 여부가 아니라 Spring 준비 완료와 DB 연결을 기다린다.
ready=false
for attempt in $(seq 1 30); do
  if curl --fail --silent --max-time 3 http://127.0.0.1:8082/internal/health >/dev/null; then
    ready=true
    break
  fi
  sleep 2
done
if [ "$ready" != true ]; then
  echo 'Scheduler readiness check failed' >&2
  exit 1
fi

# nginx:alpine supplies wget; check the frontend from its own container.
if ! docker exec toadyReactWeb wget -q -T 5 -O /dev/null http://127.0.0.1/; then
  echo 'Frontend check failed: toadyReactWeb did not serve /' >&2
  exit 1
fi
# 준비 상태 확인 이후에도 종료·재시작된 컨테이너가 없는지 확인한다.
for service in toadyWebApi toadyReactWeb toadyScheduler; do
  running=$(docker inspect --format '{{.State.Running}}' "$service" 2>/dev/null || echo missing)
  restarting=$(docker inspect --format '{{.State.Restarting}}' "$service" 2>/dev/null || echo missing)
  if [ "$running" != true ] || [ "$restarting" != false ]; then
    echo "Container check failed: $service (running=$running, restarting=$restarting)" >&2
    exit 1
  fi
done
# 동네 실거래는 선택 기능이라 배포를 막지 않는다. 다만 서버 환경 설정 누락이 조용히 지나가지 않도록 경고한다.
if ! curl --fail --silent --max-time 3 http://127.0.0.1:8080/api/v1/research/capabilities | grep -q '"tradesEnabled":true'; then
  echo 'WARNING: Neighborhood trades are disabled. Set MOLIT_TRADE_SERVICE_KEY in toadyWebApi/.env.prod and recreate the API container.' >&2
fi
# API가 필요로 하는 테이블 중 DB에 없는 것을 알려 준다. 이전 이미지(404)나 응답 실패는 건너뛴다.
# 일부 기능만 숨겨지는 선택 테이블일 수 있으므로 배포를 막지 않고 database.sql 적용을 안내한다.
schema=$(curl --fail --silent --max-time 5 http://127.0.0.1:8080/internal/schema || true)
missing=$(printf '%s' "$schema" | tr -d '\n' | sed -n 's/.*"missing"[[:space:]]*:[[:space:]]*\[\([^]]*\)\].*/\1/p' | tr -d '" ')
if [ -n "$missing" ]; then
  echo "WARNING: Database tables missing for the API: $missing. Apply the matching sections of database.sql." >&2
fi
# 앞단 nginx(80/443)를 거친 실제 서비스 경로를 확인한다. --resolve로 서버 자신에게 접속하되 인증서 검증은 그대로 둔다.
# 연결 거부·502/503/504는 앞단 nginx나 업스트림이 실제로 응답하지 못한다는 뜻이므로 배포 실패(롤백)로 본다.
# 인증서·도메인 구성(DNS 6, TLS 35, 인증서 51/60 등)이 다른 서버에서는 배포를 막지 않도록 경고만 남긴다.
public_host=${PUBLIC_HOST:-www.toady.co.kr}
check_front() {
  path=$1
  expected=$2
  url="https://$public_host$path"
  curl_status=0
  code=$(curl --silent --max-time 5 -o /dev/null -w '%{http_code}' --resolve "$public_host:443:127.0.0.1" "$url") || curl_status=$?
  case "$curl_status" in
    0) ;;
    6|35|51|58|59|60|77|83|90|91)
      echo "WARNING: $url could not be verified through the front nginx (curl exit $curl_status: DNS/TLS/certificate). Check certificates and PUBLIC_HOST." >&2
      return 0 ;;
    *)
      echo "Front nginx check failed: $url (curl exit $curl_status, e.g. connection refused or timeout)" >&2
      return 1 ;;
  esac
  case "$code" in
    "$expected") return 0 ;;
    502|503|504|000)
      echo "Front nginx check failed: $url returned HTTP $code" >&2
      return 1 ;;
    *)
      echo "WARNING: $url returned HTTP $code through the front nginx (expected $expected)." >&2
      return 0 ;;
  esac
}
check_front / 200 || exit 1
check_front /api/v1/planner/settings 200 || exit 1
echo 'API, database connection, scheduler readiness, frontend, front nginx and container startup checks passed'
