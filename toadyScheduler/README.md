# 청약홈 데이터 수집 스케줄러

청약홈 공고·주택형·청약 결과 API를 정기적으로 조회하고 MariaDB에 적재하는 Spring Boot 애플리케이션입니다.

## 기술 스택

- Java 21
- Spring Boot 3.5.16
- Gradle 8.14.6
- Spring Data JPA
- MariaDB

## 실행

JDK 21을 설치하고, 필요하면 `JAVA_HOME`을 지정합니다. 로컬 DB 접속 정보와 청약홈 API 키는 Git에서 제외되는 `.env.local`에 보관합니다. 샘플을 복사해 실제 값으로 채운 뒤 실행합니다.

```bash
cp .env.example .env.local
# .env.local의 DB 접속 정보와 API 키를 실제 값으로 수정
bash ./run-local.sh
```

`run-local.sh`는 `JAVA_HOME` 또는 `PATH`에서 JDK 21을 찾습니다. 테스트 출력에는 테스트 결과와 애플리케이션 콘솔 로그가 포함됩니다.

IntelliJ에서 직접 실행할 때는 Project SDK와 Gradle JVM을 JDK 21로 지정하고, 실행 구성을 `com.toady.web.MainApplication`으로 설정합니다. 애플리케이션은 프로젝트 루트의 Git 제외 파일 `.env.local`에서 환경변수로 지정하지 않은 설정값을 읽으므로, IntelliJ 실행 구성에 비밀값을 다시 입력할 필요가 없습니다. 실행 구성의 Working directory는 프로젝트 루트로 둡니다.

기본 스케줄은 매일 오전 3시 15분(한국시간)이며, 오늘(한국시간) 기준 한 달 전부터 한 달 후까지 모집공고를 조회합니다. 실행 시각은 `APPLYHOME_SYNC_CRON`으로 바꿀 수 있습니다. 반복 실행은 MariaDB advisory lock으로 겹치지 않게 하고, 기존 복합 PK에는 upsert합니다. 오피스텔/도시형 주택형은 공고별 기존 행을 조회해 동일한 데이터가 이미 있으면 다시 저장하지 않습니다. 이 방식은 현재 DB 계정의 `SELECT`, `INSERT`, `UPDATE` 권한만으로 동작합니다. 최초 실행 전 `src/main/resources/db/manual/alter_urbty_ofctl_lttot_pblanc_mdl.sql`을 실행해야 합니다.

청약접수 경쟁률, 당첨가점, 특별공급 신청현황은 매일 17:00부터 20:00까지(한국시간) 10분 간격으로 조회합니다. 마지막 실행은 20:00이며, 매 회 오늘 전후 한 달 내 모집공고를 찾아 API 응답을 아래 테이블에 복합키 upsert 합니다. 최초 실행 전에 `src/main/resources/db/manual/create_applyhome_competition_tables.sql`을 실행해야 합니다. 시간은 `APPLYHOME_COMPETITION_POLL_CRON` 및 `APPLYHOME_COMPETITION_FINAL_CRON` 환경변수로 바꿀 수 있습니다.

경쟁률 API 전체 이력을 적재하려면 DDL 적용 후 `bash ./run-local.sh test --tests '*ApplyhomeCompetitionBackfillIntegrationTest' -Dapplyhome.competition.integration.enabled=true`를 실행합니다. 기본 조회 기간은 2017-01-01부터 오늘(서울 기준)까지이며 `-Dapplyhome.competition.test.start-date=YYYY-MM-DD` 및 `-Dapplyhome.competition.test.end-date=YYYY-MM-DD`로 조정할 수 있습니다.

청약홈 문서의 5개 분양정보 API를 모집공고일 날짜 범위로 조회하고, 해당 공고의 주택형 API를 이어서 호출합니다. 통합 테스트에서 날짜를 지정하지 않으면 오늘(서울 기준) 기준 한 달 전부터 한 달 후까지 조회합니다. 과거 데이터 적재 등 별도 범위는 시스템 프로퍼티로 지정할 수 있습니다. `clean` 또는 기본 `test` 실행은 실제 DB/API 호출을 하지 않습니다.

```bash
bash ./run-local.sh test --tests '*ApplyhomeBackfillIntegrationTest' \
  -Dapplyhome.integration.enabled=true
```

시작일과 종료일을 직접 지정하려면 `-Dapplyhome.test.start-date=YYYY-MM-DD` 및 `-Dapplyhome.test.end-date=YYYY-MM-DD`를 추가합니다. 시작일만 지정한 경우 종료일 기본값은 오늘(서울 기준)부터 한 달 후입니다.

각 날짜 범위 처리는 재실행해도 기존 레코드가 중복되지 않도록 저장합니다. Hibernate의 스키마 자동 변경은 비활성화되어 있습니다.

## 테스트

```bash
bash ./run-local.sh test
```

## Docker 이미지

GitHub Actions 등에서 애플리케이션 이미지를 빌드할 때 프로젝트 루트에서 실행합니다. Docker 이미지에는 환경 파일과 비밀값을 포함하지 않습니다.

```bash
docker build -t toady-scheduler .
```

운영 환경에서는 컨테이너 실행 시 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `APPLYHOME_API_KEY`를 환경변수로 주입하세요. 선택적으로 `APPLYHOME_SYNC_CRON`, `APPLYHOME_COMPETITION_POLL_CRON`, `APPLYHOME_COMPETITION_FINAL_CRON`도 설정할 수 있으며, 미설정 시 위 기본 한국시간 스케줄을 사용합니다.
