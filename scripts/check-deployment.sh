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
docker exec toadyReactWeb wget -q -T 5 -O /dev/null http://127.0.0.1/
# 준비 상태 확인 이후에도 종료·재시작된 컨테이너가 없는지 확인한다.
for service in toadyWebApi toadyReactWeb toadyScheduler; do
  test "$(docker inspect --format '{{.State.Running}}' "$service")" = true
  test "$(docker inspect --format '{{.State.Restarting}}' "$service")" = false
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
# 인증서·도메인 구성이 다른 서버에서도 배포를 막지 않도록 경고만 남긴다.
public_host=${PUBLIC_HOST:-www.toady.co.kr}
if ! curl --fail --silent --max-time 5 -o /dev/null --resolve "$public_host:443:127.0.0.1" "https://$public_host/"; then
  echo "WARNING: https://$public_host/ did not respond through the front nginx. Check the nginx container, its upstreams and certificates." >&2
fi
echo 'API, database connection, scheduler readiness, frontend and container startup checks passed'
