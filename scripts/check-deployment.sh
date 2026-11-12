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
echo 'API, database connection, scheduler readiness, frontend and container startup checks passed'
