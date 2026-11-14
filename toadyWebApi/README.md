# TOADY API (`toadyWebApi`)

공고 조회·실거래 분석·회원·커뮤니티·알림을 담당하는 Spring Boot API입니다. 수집기가 DB에 쌓은 청약홈 데이터를 화면에 맞게 가공하고, 국토교통부 실거래와 카카오 주소 검색을 붙여 '분양가 vs 주변 시세'와 실거래 지도를 제공합니다. 검색 노출용 서버 HTML과 사이트맵, 공유 미리보기 이미지도 여기서 만듭니다.

## 주요 기능

| 영역 | 내용 |
| --- | --- |
| 공고 | 5개 유형 통합 검색·페이지 조회, 전체 기간 자동완성, 상세·주택형, 경쟁률·당첨가점·특별공급, 모집공고문 PDF 링크 |
| 실거래·위치 | 단지별 실거래 지도 데이터, 분양가 vs 주변 시세(신축 기준), 동네 실거래, 가까운 역·학교·마트(직선거리) |
| 개인화 | 관심 공고·진행 단계·메모, 맞춤 청약 판정, 가점 비교, 개인 캘린더 구독(ICS), 이메일 일정 알림, 웹 푸시 |
| 회원 | 가입·메일 인증·로그인(JWT)·비밀번호 찾기, 내 정보(닉네임·비밀번호·프로필 캐릭터·탈퇴), 받은 답글 |
| 커뮤니티 | 공고별 이야기(댓글·답글, 커서 페이지), 경쟁률 예상 투표 |
| 공개 페이지 | 공고·생활권·이번 주·무순위·FAQ 서버 HTML, 사이트맵, 공유용 OG 이미지(PNG) |
| 측정 | 익명 페이지 조회·행동 기록 |

## 기술 스택

Java 21 · Spring Boot 3.5 · Spring Data JPA(Hibernate) · JdbcTemplate · Spring Security · JJWT · MariaDB · Springdoc OpenAPI · JUnit 5 · H2 · Docker(Eclipse Temurin 21)

## 구조

```text
com.toady.web
├── app
│   ├── announcement   # 공고 통합 검색·상세·경쟁률·댓글
│   ├── planner        # 나의 청약·맞춤 청약·가점 비교·캘린더·메일 알림·공개 HTML·사이트맵·공유 이미지
│   │   └── research   # 국토부 실거래·카카오 좌표·단지별 가격·분양가 vs 주변 시세·주변 시설
│   ├── user           # 가입·로그인·내 정보(AccountService)·프로필 캐릭터
│   ├── certify        # 메일 인증·비밀번호 재설정
│   ├── push           # 웹 푸시 구독·하루 한 번 묶음 발송(RFC 8291/8292 직접 구현)
│   └── analytics      # 익명 페이지 조회·행동 기록
└── core               # 보안(JWT·요청 제한·내부 경로 차단)·예외·응답·헬스체크·로깅
```

## 설계 포인트

### 5개 유형 통합 검색
유형마다 테이블·접수일 필드가 달라 각 Repository에 같은 조건(유형·지역·키워드·기간)을 적용한 뒤 공통 `AnnouncementListItem`으로 바꿔 접수 시작일 기준으로 합칩니다. 깊은 페이지의 선행 조회량을 막기 위해 `(page+1)×size` 상한을 둡니다. 목록의 댓글 수는 반환할 공고 키를 한 번에 집계합니다.

### 실거래 지도와 외부 API 한도
- 국토교통부 매매 실거래는 시군구·월 단위로 캐시하고, 응답 시간 한도 안에 못 받은 달은 `partial`로 먼저 돌려준 뒤 화면이 이어서 받습니다.
- 호출은 동시 3개·0.12초 간격으로 조절하고, 429·일일 한도 초과(resultCode 22)·키 오류를 받으면 차단기를 켜 모든 호출을 잠시 멈춥니다(초당 한도 1분, 일일 한도 자정까지, 키 오류 1시간). 공공데이터포털 게이트웨이 오류 형식(`returnReasonCode`)도 같이 처리합니다.
- 실거래에는 좌표가 없어 지번을 카카오 로컬 API로 좌표로 바꾸고 `TB_APT_COMPLEX_LOCATION`에 남깁니다. 응답의 법정동 코드·지번이 일치할 때만 저장해 다른 단지 좌표로 오염되지 않게 합니다.
- 분양가 vs 주변 시세는 최근 최대 6개월·전용 ±5㎡ 거래의 1~3사분위 범위로 비교하고, 준공 10년 이내 거래가 충분하면 신축 기준을 씁니다. 접수 중·예정 공고는 매일 미리 계산해 `TB_PRICE_GAP`에 둡니다.

### 인증·보안
- JWT에 비밀번호 기반 확인값을 넣어 비밀번호 변경·잠금·탈퇴 시 다른 기기의 토큰을 즉시 거부합니다. 만료 30분 전에 자동 갱신하되 최초 로그인 시각(`tca`)을 이어받아 최대 14일이 지나면 다시 로그인하게 하고, 닉네임 변경으로 새로 받는 토큰도 이 시각을 이어받습니다.
- 로그인 실패는 IP·이메일 기준으로, 현재 비밀번호 확인(비밀번호 변경·탈퇴)은 IP와 회원 기준으로 제한합니다. 계정 변경은 회원 행 잠금으로 직렬화하고 잠금 안에서 제한을 다시 검사합니다.
- `/internal/**`은 컨테이너 내부 요청만 받고(`InternalPathFilter`), 요청 수 제한은 경로별 규칙으로 둡니다(`RequestLimitFilter`). 문자열로 만드는 SQL은 고정 허용 목록의 테이블명과 `?` 자리표시자만 씁니다.
- 외부 호출(청약홈·국토부·카카오·웹 푸시)은 호스트 허용 목록, 리다이렉트 금지, 전체 시간 제한, 응답 크기 제한을 둡니다.

### 메일 발송 한도
Gmail 한 계정의 하루 한도 안에서 인증 메일이 막히지 않도록 최근 24시간 발송 시도를 DB(`TB_MAIL_BUDGET`·`TB_MAIL_ATTEMPT`)로 세고 인증용 여유분을 남깁니다. 가입 인증 메일은 별도 상한을 둬 무작위 가입 반복이 비밀번호 찾기 메일을 막지 못하게 합니다. 발신 주소(`MAIL_FROM`)를 설정할 수 있어 메일 서비스를 옮길 때 환경변수만 바꾸면 됩니다.

### 웹 푸시
RFC 8291(aes128gcm)·RFC 8292(VAPID ES256)를 JDK 기본 암호 기능으로 직접 구현해(`WebPushCrypto`) 외부 암호 라이브러리 없이 보내고, RFC 부록 테스트 벡터로 검증합니다. 구독마다 하루 한 번(18시 무렵) 관심 공고의 내일 일정·생활권 새 공고·주간 브리핑을 한 알림으로 묶고, 404·410을 받은 구독은 지웁니다.

## API 요약

| 경로 | 내용 | 접근 |
| --- | --- | --- |
| `GET /api/v1/announcements`, `/suggestions` | 통합 검색, 전체 기간 자동완성 | 공개 |
| `GET /api/v1/{type}/announcements/{house}/{notice}` (+`/competition-rates`, `/scores`, `/special-supply-status`) | 상세·경쟁률·가점·특별공급 | 공개 |
| `GET·POST·PUT·DELETE …/{house}/{notice}/comments` | 이야기(댓글·답글) | 조회 공개, 작성·수정·삭제 회원 |
| `GET /api/v1/research/complexes`, `/sales`, `/price-gap`, `/transactions`, `/nearby` | 실거래 지도·분양가 비교·동네 실거래·주변 시설 | 공개 |
| `/api/v1/me/favorites`, `/api/v1/me/calendar-feed`, `/api/v1/planner/*` | 관심 공고·캘린더 구독·요약·결과 | 회원(요약은 공개) |
| `/api/v1/me/account/*` | 내 정보, 닉네임·비밀번호·캐릭터 변경, 내가 쓴 이야기, 받은 답글, 탈퇴 | 회원 |
| `/webApi/user/*`, `/webApi/certify/*` | 가입·로그인·메일 인증·비밀번호 재설정 | 공개 |
| `/api/v1/push/*` | 웹 푸시 설정·구독·해지 | 공개 |
| `/api/v1/public/*` | 공개 HTML·사이트맵·OG 이미지·맞춤 청약·가점 비교 데이터 | 공개 |
| `POST /api/v1/analytics/page-views`, `/events` | 익명 측정 | 공개 |
| `GET /internal/health`, `/internal/schema` | 준비 상태(DB 연결)·필수 테이블 점검 | 컨테이너 내부 |

로컬에서 실행하면 `http://localhost:8081/swagger-ui/index.html`에서 전체 명세를 볼 수 있습니다.

## 실행

필요 환경: Java 21, MariaDB(스키마 준비 필요, `ddl-auto=none`).

```bash
cp .env.example .env        # DB_URL·DB_USERNAME·DB_PASSWORD, JWT_SECRET(openssl rand -base64 64)
./gradlew bootRun           # SERVER_PORT=8081(프런트 개발 서버의 프록시 대상)
```

| 환경변수 | 용도 |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | MariaDB 접속 |
| `JWT_SECRET` | JWT 서명 키(64바이트 이상 Base64) |
| `MAIL_HOST`·`MAIL_PORT`·`MAIL_USERNAME`·`MAIL_PASSWORD`, `MAIL_FROM`·`MAIL_FROM_NAME` | SMTP와 보내는 사람(비우면 SMTP 계정) |
| `GMAIL_ROLLING_LIMIT`, `GMAIL_AUTH_RESERVE`, `PLANNER_REMINDERS_ENABLED` | 24시간 발송 상한, 인증용 여유분, 일정 알림 사용 |
| `MOLIT_TRADE_SERVICE_KEY` | 국토교통부 아파트 매매 실거래 인증키(서버 전용) |
| `KAKAO_REST_API_KEY` | 카카오 로컬 API(주소 좌표·주변 시설, 서버 전용) |
| `WEB_PUSH_VAPID_PUBLIC_KEY`·`WEB_PUSH_VAPID_PRIVATE_KEY`·`WEB_PUSH_SUBJECT`, `PUSH_DIGEST_ENABLED` | 웹 푸시 키와 발송 사용 |

키가 없는 기능(실거래·좌표·푸시·메일 알림)은 꺼진 채로 나머지가 동작합니다. 필요한 테이블은 루트 [database.sql](../database.sql)의 구역 번호로 관리합니다.

## 테스트

```bash
./gradlew test                                   # H2(MariaDB 모드), 외부 연결 없음 — 168개
TOADY_BUILD_DIR=build/cli ./gradlew test         # IDE에서 서버를 띄워 둔 채 실행할 때(출력 폴더 분리)
./gradlew mariadbTest                            # 개발 DB에서 실제 PK·SQL·DML 확인(테스트 행만 쓰고 롤백)
```

| 테스트 | 내용 |
| --- | --- |
| `ApplicationFlowRegressionTest` | 가입 → 메일 인증 → 로그인 → 댓글 작성·수정·삭제·소유권 → 비밀번호 변경 후 기존 토큰 거부, 요청 제한 |
| `AccountSettingsTest` | 내 정보 요약, 닉네임 변경(최초 로그인 시각 유지), 비밀번호 변경·탈퇴, 회원 기준 대입 제한, 받은 답글 |
| `CertifyTransactionTest` | 가입 실패 롤백, 인증 코드 동시 사용·재발송 |
| `NeighborhoodResearchServiceTest`, `ComplexPriceServiceTest` | 실거래 응답 파싱, 일부 응답, 차단기, 호출 속도 조절 |
| `PushIntegrationTest` | 실제 푸시 서비스 대신 전송을 가로채 복호화해 내용·410 삭제·하루 1회 확인 |
| `MainApplicationTests`, `PublicPagesUnitTest` 등 | 통합 조회·날짜 필터·자동완성·공개 HTML·캘린더 파일 |

Docker 이미지는 `docker build -t toady-web-api .`로 만들며, 공유 이미지의 한글을 위해 Noto CJK 폰트를 함께 설치합니다.
