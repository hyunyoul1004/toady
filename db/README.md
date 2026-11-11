# 공유 DB 변경 관리

API와 수집기가 공유하는 스키마의 신규 변경은 이 디렉터리에서 관리합니다.
두 애플리케이션의 자동 DDL 설정은 계속 `none`으로 유지합니다. 앱 기동이나
일반 배포에서 마이그레이션을 자동 실행하지 않습니다. 변경 담당자는 배포 전에
별도 유지보수 단계로 한 번 실행하고, 두 앱과의 호환성을 확인합니다.

## 기존 DB 도입

현재 저장소에는 전체 초기 스키마가 없습니다. 버전 1은 **이미 구성된 기존 DB**를
의미하며, 빈 DB를 만드는 초기화 스크립트가 아닙니다. 기존 수동 SQL 중 경쟁률
테이블과 도시형 주택형 PK 변경은 이미 적용되어 있는지 먼저 확인해야 합니다.
과거 SQL은 `toadyScheduler/src/main/resources/db/manual`에 참고용으로 남겨 두며
자동 재실행하지 않습니다. 특히 도시형 PK 변경 SQL은 재실행하면 실패합니다.

1. DB 백업과 복원 점검 후 운영 DB의 복제본에서 먼저 검증합니다.
2. 공고·주택형·경쟁률·회원 테이블 및 도시형 주택형의 `ID` PK를 확인합니다.
3. 루트 `.env.migrations` 파일에 아래 값을 설정합니다. 이 파일은 Git에서 제외됩니다.

   ```dotenv
   MIGRATION_DB_URL=jdbc:mariadb://mariadb:3306/실제_DB명
   MIGRATION_DB_USERNAME=마이그레이션_계정
   MIGRATION_DB_PASSWORD=실제_비밀번호
   MIGRATION_NETWORK=toady_default
   ```

4. 저장소 루트에서 다음 명령으로 상태를 확인하고 기존 DB의 버전 1을 등록합니다.
   `baseline`은 스키마를 검사하거나 생성하는 명령이 아니므로 사전 확인이 필요합니다.

   ```bash
   docker compose --env-file .env.migrations -f compose.migrations.yml run --rm migrate info
   docker compose --env-file .env.migrations -f compose.migrations.yml run --rm migrate baseline
   docker compose --env-file .env.migrations -f compose.migrations.yml run --rm migrate migrate
   docker compose --env-file .env.migrations -f compose.migrations.yml run --rm migrate validate
   ```

Compose v1 환경에서는 `docker compose` 대신 `docker-compose`를 사용합니다.
위 네트워크는 기존 `docker-compose -p toady up -d`가 생성한 네트워크여야 합니다.

## 관리 범위와 후속 변경

첫 버전 변경인 `V2__create_announcement_comments.sql`은 기존 댓글 테이블 생성 SQL과
동일합니다. 테이블이 이미 있으면 유지하고, 없으면 생성합니다. 기존 테이블의
열·인덱스 불일치를 자동 보정하지는 않습니다. 운영 테이블 정의와 SQL의 일치 여부를
도입 전에 확인해야 합니다. 기존 `toadyWebApi/src/main/resources/db/schema` SQL은
과거 참고 자료이며 앞으로 수정할 원본은 `db/migration`입니다.

- 새 변경은 `V3__설명.sql`, `V4__설명.sql` 순으로 추가합니다.
- 적용된 파일은 수정하지 않습니다. Flyway가 이력과 체크섬을 검사합니다.
- 두 앱 모두 실행 가능한 확장 변경을 먼저 배포하고, 기존 열 제거는 후속 배포로 나눕니다.
- MariaDB DDL은 자동 롤백을 기대하지 않습니다. 실패 시 실제 스키마를 확인하고
  후속 수정 또는 백업 복원으로 대응합니다. 상태 확인 없이 `repair`하지 않습니다.
- 앱 이미지 롤백은 DB 변경을 되돌리지 않습니다.

이 구성은 기존 DB의 버전 관리 도입 경로입니다. 전체 DB의 신규 구축에는 별도로
검증된 전체 스키마 덤프 또는 초기화 마이그레이션이 필요합니다.
