# toadyWebApi

TOADY의 사용자 API입니다. 수집기가 DB에 쌓아 둔 청약홈 데이터를 화면에 맞게 가공하고, 국토교통부 실거래와 카카오 주소 검색을 붙여 분양가와 주변 시세 비교, 실거래 지도를 만듭니다. 회원, 이야기(댓글), 쪽지, 알림도 여기서 처리하고, 검색엔진용 서버 HTML과 사이트맵, 공유 미리보기 이미지도 만듭니다.

Java 21, Spring Boot 3.5, Spring Data JPA, JdbcTemplate, Spring Security, JJWT, MariaDB, Redis(Lettuce)

## 하는 일

| 영역 | 내용 |
| --- | --- |
| 공고 | 5개 유형 통합 검색과 페이지 조회, 전체 기간 자동완성, 상세·주택형, 경쟁률·당첨가점·특별공급, 모집공고문 링크 |
| 실거래·위치 | 실거래 지도 데이터, 분양가와 주변 시세 비교, 동네 실거래, 가까운 역·학교·마트 |
| 개인화 | 관심 공고와 진행 단계·메모, 맞춤 청약, 가점 비교, 캘린더 구독(ICS), 이메일 일정 알림, 웹 푸시 |
| 회원 | 가입, 메일 인증, 로그인(JWT), 비밀번호 찾기, 닉네임·비밀번호·프로필 캐릭터 변경, 탈퇴 |
| 커뮤니티 | 공고별 이야기(댓글·답글), 경쟁률 예상 투표, 1:1 쪽지, 차단, 신고 |
| 운영 | 사이트 공지, 운영 안내, 금칙어, 공고 숨김·정정, 서비스 설정. 값은 관리자 사이트가 DB에 넣고 API는 60초마다 다시 읽음 |
| 공개 페이지 | 공고·생활권·이번 주·무순위·FAQ 서버 HTML, 사이트맵, 새 공고 RSS, 공유 이미지(PNG) |
| 측정 | 익명 페이지 조회, 행동 기록, 검색어 통계 |

## 패키지

```text
com.toady.web
├── app
│   ├── announcement   공고 통합 검색, 상세, 경쟁률, 이야기
│   ├── planner        나의 청약, 맞춤 청약, 가점 비교, 캘린더, 메일 알림, 공개 HTML, 사이트맵, RSS
│   │   └── research   국토부 실거래, 카카오 좌표, 단지별 가격, 시세 비교, 주변 시설
│   ├── community      쪽지, 차단, 신고
│   ├── operation      공지, 설정, 금칙어, 제재, 공고 숨김, 관리자용 내부 API
│   ├── user           가입, 로그인, 내 정보(AccountService), 프로필 캐릭터
│   ├── certify        메일 인증, 비밀번호 재설정
│   ├── push           웹 푸시 구독과 하루 한 번 묶음 발송
│   └── analytics      익명 페이지 조회와 행동 기록
└── core
    ├── security       JWT, 요청 제한, 로그인 실패 제한, 내부 경로 차단
    ├── redis          Redis 연결, 요청 기록 창(SlidingWindows), 공유 캐시
    └── health ...     헬스체크, 예외, 응답, 로깅
```

## 구현하면서 정한 것들

### 5개 유형 통합 검색
유형마다 테이블과 접수일 필드가 달라서, 유형별 Repository에 같은 조건(유형·지역·키워드·기간)을 걸고 공통 `AnnouncementListItem`으로 바꾼 뒤 접수 시작일 순으로 합칩니다. 페이지가 깊어질수록 유형별로 미리 읽는 양이 커지기 때문에 `(page+1)×size`에 상한을 뒀습니다. 숨김 처리된 공고는 Specification 하나로 모든 검색에서 빠집니다.

### 실거래 지도와 외부 API 한도
- 국토부 실거래는 시군구·월 단위로 메모리에 캐시하고, 그 다음 단계로 Redis에 6시간 둡니다. 정해진 시간 안에 못 받은 달은 `partial`로 먼저 돌려주고 화면이 이어서 받습니다.
- 호출은 동시에 3개, 0.12초 간격입니다. 429, 하루 한도 초과(resultCode 22), 키 오류를 받으면 모든 호출을 잠시 멈춥니다(초당 한도 1분, 하루 한도 자정까지, 키 오류 1시간).
- 실거래에는 좌표가 없어서 지번을 카카오로 좌표 변환해 `TB_APT_COMPLEX_LOCATION`에 남깁니다. 법정동 코드와 지번이 맞을 때만 저장해서 엉뚱한 단지 좌표가 섞이지 않게 했습니다.
- 분양가 비교는 최근 6개월, 전용 ±5㎡ 거래의 1~3사분위 범위로 합니다. 준공 10년 이내 거래가 충분하면 신축 기준을 씁니다. 접수 중·예정 공고는 매일 미리 계산해 둡니다(`TB_PRICE_GAP`).

### 로그인과 보안
- JWT에 비밀번호에서 만든 확인값을 넣어서, 비밀번호 변경·잠금·탈퇴 때 다른 기기의 토큰이 바로 막힙니다. 만료 30분 전에 자동으로 갱신하되 처음 로그인한 시각(`tca`)을 이어받아 14일이 지나면 다시 로그인해야 합니다.
- 요청 제한(`RequestLimitFilter`)과 로그인 실패 제한(`LoginAttemptLimiter`)은 Redis 정렬 집합에 기록합니다. 한도 확인과 기록은 Lua 스크립트 하나로 처리합니다. 배포해도 기록이 남고, API를 여러 대 띄워도 같이 셉니다.
- Redis가 응답하지 않으면 300ms 안에 포기하고 30초 동안 메모리로 대신합니다. 그동안 사이트는 그대로 돌아갑니다.
- 계정 변경은 회원 행을 잠근(`SELECT ... FOR UPDATE`) 뒤 잠금 안에서 제한을 다시 검사합니다.
- `/internal/**`은 앞단 nginx를 거친 요청(`X-Real-IP`가 있는 요청)을 막고, 관리자 사이트용 경로는 32자 이상 공유 토큰을 요구합니다.
- 외부 호출(청약홈, 국토부, 카카오, 웹 푸시)에는 호스트 허용 목록, 리다이렉트 금지, 시간 제한, 응답 크기 제한을 둡니다.

### 메일 발송 한도
Gmail 계정 하나의 하루 한도 안에서 인증 메일이 막히지 않도록 최근 24시간 발송 시도를 DB(`TB_MAIL_BUDGET`, `TB_MAIL_ATTEMPT`)로 세고 인증용 여유분을 남깁니다. 가입 인증 메일에는 따로 상한을 둬서, 무작위 가입을 반복해도 비밀번호 찾기 메일이 막히지 않습니다. 발신 주소는 `MAIL_FROM`으로 바꿀 수 있습니다.

### 웹 푸시
RFC 8291(aes128gcm), RFC 8292(VAPID ES256)를 JDK 기본 암호 기능으로 직접 구현했고(`WebPushCrypto`), RFC 부록의 테스트 벡터로 확인합니다. 구독마다 하루 한 번(18시 무렵) 관심 공고 일정, 생활권 새 공고, 주간 브리핑을 알림 하나로 묶어 보냅니다. 404·410을 받은 구독은 지웁니다.

### JPA와 JdbcTemplate
행 하나를 읽고 규칙에 따라 고치는 일(회원, 이야기)은 JPA로 하고, 동시에 숫자를 올리는 카운터나 엔티티가 없는 운영 테이블은 JdbcTemplate으로 씁니다. 관리자 사이트가 같은 행의 다른 컬럼을 따로 바꾸기 때문에 회원과 이야기 엔티티에는 `@DynamicUpdate`를 붙여 바뀐 컬럼만 UPDATE합니다.

## API

| 경로 | 내용 | 접근 |
| --- | --- | --- |
| `GET /api/v1/announcements`, `/suggestions` | 통합 검색, 자동완성 | 공개 |
| `GET /api/v1/{type}/announcements/{house}/{notice}` (+`/competition-rates`, `/scores`, `/special-supply-status`) | 상세, 경쟁률, 가점, 특별공급 | 공개 |
| `…/{house}/{notice}/comments` (+`/pinned`) | 이야기 목록·작성·수정·삭제, 운영팀 고정 글 | 조회 공개, 쓰기 회원 |
| `GET /api/v1/research/complexes`, `/sales`, `/price-gap`, `/transactions`, `/nearby` | 실거래 지도, 분양가 비교, 동네 실거래, 주변 시설 | 공개 |
| `/api/v1/me/favorites`, `/api/v1/me/calendar-feed`, `/api/v1/planner/*` | 관심 공고, 캘린더 구독, 요약 | 회원(요약은 공개) |
| `/api/v1/me/account/*` | 내 정보, 닉네임·비밀번호·캐릭터 변경, 내가 쓴 이야기, 받은 답글, 탈퇴 | 회원 |
| `GET /api/v1/members/{userSeq}` | 프로필 카드 | 공개 |
| `/api/v1/me/messages*`, `/api/v1/me/blocks*`, `POST /api/v1/me/reports` | 쪽지, 차단, 신고 | 회원 |
| `GET /api/v1/me/notices`, `GET /api/v1/public/notices` | 나에게 온 운영 안내, 사이트 공지 | 회원 / 공개 |
| `/webApi/user/*`, `/webApi/certify/*` | 가입, 로그인, 메일 인증, 비밀번호 재설정 | 공개 |
| `/api/v1/push/*` | 웹 푸시 설정·구독·해지 | 공개 |
| `/api/v1/public/*` | 공개 HTML, 사이트맵, RSS, 공유 이미지, 맞춤 청약·가점 비교 데이터 | 공개 |
| `POST /api/v1/analytics/page-views`, `/events`, `/search` | 익명 측정 | 공개 |
| `/internal/admin/*` | 관리자 사이트용: 푸시 발송, 인증 메일 다시 보내기, 탈퇴 대행 | 내부 토큰 |
| `GET /internal/health`, `/internal/schema` | 준비 상태(DB 연결, Redis 상태), 빠진 테이블 점검 | 컨테이너 내부 |

로컬에서 띄우면 `http://localhost:8081/swagger-ui/index.html`에서 전체 명세를 볼 수 있습니다. 운영에서는 꺼 둡니다.

## 실행

Java 21과 MariaDB가 필요합니다(`ddl-auto=none`이라 테이블은 미리 있어야 함).

```bash
cp .env.example .env.local   # DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET(openssl rand -base64 64)
./gradlew bootRun            # 8081, 프런트 개발 서버가 이쪽으로 요청을 넘김
```

| 환경변수 | 용도 |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | MariaDB 접속 |
| `JWT_SECRET` | JWT 서명 키(64바이트 이상, Base64) |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | 요청 제한·캐시용 Redis. 호스트를 비우면 메모리로 동작합니다. 운영은 Compose가 `REDIS_HOST=redis`를 넣습니다 |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, `MAIL_FROM_NAME` | SMTP와 보내는 사람 |
| `GMAIL_ROLLING_LIMIT`, `GMAIL_AUTH_RESERVE`, `PLANNER_REMINDERS_ENABLED` | 24시간 발송 상한, 인증용 여유분, 일정 알림 사용 |
| `MOLIT_TRADE_SERVICE_KEY` | 국토교통부 아파트 매매 실거래 인증키 |
| `KAKAO_REST_API_KEY` | 카카오 로컬 API(좌표, 주변 시설) |
| `WEB_PUSH_VAPID_PUBLIC_KEY`, `WEB_PUSH_VAPID_PRIVATE_KEY`, `WEB_PUSH_SUBJECT`, `PUSH_DIGEST_ENABLED` | 웹 푸시 키와 발송 사용 |
| `ADMIN_API_TOKEN` | 관리자 사이트가 부르는 내부 API 토큰(32자 이상, 비우면 끔) |
| `DM_MIN_ACCOUNT_HOURS`, `DM_REQUIRE_STORY`, `DM_DAILY_NEW_THREADS`, `DM_DAILY_MESSAGES` | 쪽지 시작 조건과 하루 한도의 기본값(관리자 설정이 있으면 그 값을 씀) |

키가 없는 기능(실거래, 좌표, 푸시, 메일 알림)은 꺼진 채로 나머지가 동작합니다. 필요한 테이블은 루트의 [database.sql](../database.sql)에 구역 번호로 있습니다.

## 테스트

```bash
./gradlew test                             # H2(MariaDB 모드), 외부 연결 없음, 213개
TOADY_BUILD_DIR=build/cli ./gradlew test   # IDE로 서버를 띄워 둔 채 돌릴 때 출력 폴더를 나눔
./gradlew mariadbTest                      # 개발 DB에서 실제 SQL 확인(테스트 행만 쓰고 롤백)
```

| 테스트 | 확인하는 것 |
| --- | --- |
| `ApplicationFlowRegressionTest` | 가입 → 메일 인증 → 로그인 → 이야기 작성·수정·삭제 → 비밀번호 변경 뒤 기존 토큰 거부 |
| `AccountSettingsTest`, `CertifyTransactionTest` | 닉네임·비밀번호 변경, 탈퇴, 대입 제한, 인증 코드 동시 사용 |
| `CommunityTest`, `DmRulesTest` | 쪽지 규칙과 한도, 차단, 신고 |
| `NeighborhoodResearchServiceTest`, `ComplexPriceServiceTest` | 실거래 응답 파싱, 일부 응답, 차단기, 호출 간격 |
| `SlidingWindowsTest`, `RedisIntegrationTest` | 요청 기록 창, Redis가 없을 때 메모리로 대신하는 동작, 실제 Redis에서 Lua 스크립트(CI에서만) |
| `PushIntegrationTest`, `WebPushCryptoTest` | 푸시 전송을 가로채 복호화해 내용 확인, RFC 테스트 벡터 |
| `PublicPagesUnitTest`, `AnnouncementSitemapTest`, `AnnouncementFeedTest` | 공개 HTML, 사이트맵, RSS |

Docker 이미지는 `docker build -t toady-web-api .`로 만듭니다. 공유 이미지에 한글을 그리기 위해 Noto CJK 폰트를 같이 설치합니다.
