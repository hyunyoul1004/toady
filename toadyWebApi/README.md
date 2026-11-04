# TOADY API

부동산·분양 정보와 커뮤니티를 제공하는 Spring 기반 API 서버입니다.

## 프로젝트 개요

청약·분양 정보와 공급 위치, 주변 시세 및 커뮤니티를 한 곳에서 제공하는 부동산 플랫폼 프로젝트입니다.

- API 도메인: https://api.toady.co.kr
- 웹사이트: https://www.toady.co.kr

### 기존 화면

![TOADY 화면 1](/uploads/bd94f337d704f0b9f5bb975e92166c60/KakaoTalk_Photo_2022-07-14-13-13-29.jpeg)
![TOADY 화면 2](/uploads/f1d323192eb5baa69ca9d4c6ce8124ea/KakaoTalk_Photo_2022-07-14-13-09-18_003.jpeg)
![TOADY 화면 3](/uploads/31accd5e7c899de2a49062eb8c049283/KakaoTalk_Photo_2022-07-14-13-09-18_002.jpeg)
![TOADY 화면 4](/uploads/1e63c19b94510c759714171c048b00f0/KakaoTalk_Photo_2022-07-14-13-09-18_001.jpeg)

## 기술 스택

- Java 21
- Spring Boot 3.5.16 / Spring Security 6 / Spring Data JPA
- Gradle Wrapper 8.14.4
- MariaDB, Hibernate 6
- Springdoc OpenAPI 2.8.17, JJWT 0.12.6

기존 계정·이메일 인증·게시판 API는 유지합니다. 예전 청약 일정 조회 API와 엔티티는 제거했습니다. 분양 공고 및 주택형 테이블은 읽기 전용으로 사용하며, Hibernate DDL 자동 변경은 꺼져 있습니다.

## 로컬 설정과 실행

1. JDK 21을 IntelliJ에 Project SDK로 등록합니다. 로컬 JDK 경로는 `/Users/youn/dev/dev_tool/jdk/jdk-21.0.2.jdk/Contents/Home`입니다.
2. 처음 실행한다면 `.env.example`을 `.env`로 복사해 공통 환경변수를 설정합니다. 로컬 프로필은 선택적으로 `.env.local`도 읽으며, 이 파일의 값이 `.env`보다 우선합니다. JWT 키는 `openssl rand -base64 64`로 생성합니다. `.env`와 `.env.local`은 Git에서 제외되며, 비밀값을 코드나 커밋에 넣지 마세요.
3. 터미널에서 다음 명령으로 JDK 21을 선택하고 실행합니다.

```bash
export JAVA_HOME="/Users/youn/dev/dev_tool/jdk/jdk-21.0.2.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export SPRING_PROFILES_ACTIVE=local
java -version
./gradlew test
./gradlew bootRun
```

`application.yml`에 공통 설정을 모으고 `application-local.yml`, `application-dev.yml`, `application-real.yml`에는 서버별 차이만 둡니다. DB·메일·JWT 비밀값은 YML이 아니라 `.env` 또는 서버 환경변수에서 읽습니다. IntelliJ의 Gradle JVM은 `21`로 맞추고, 공유 실행 구성 **Toady Web API**를 사용합니다. 이 구성은 `local` 프로필과 지정된 JDK 21을 사용합니다. 로컬 프로필 기본 포트는 `8081`, 개발·운영 프로필은 `8080`입니다.

로컬 프로필은 Gmail SMTP(`smtp.gmail.com:587`, `dukkeoba@gmail.com`)를 사용합니다. Gmail 앱 비밀번호는 `.env.local`에 `MAIL_PASSWORD=새로운앱비밀번호`로 설정하세요. 메일 발송이 실패하면 회원가입 API는 `502 Bad Gateway`와 실패 응답을 반환하며, 해당 가입과 인증 레코드는 저장되지 않습니다. 개발·운영 환경은 `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`를 별도로 설정해야 합니다.

로컬 프로필은 P6Spy를 통해 바인딩 값이 적용된 SQL을 콘솔에 출력합니다. 따라서 SQL의 `?`가 실제 값으로 표시됩니다. 이 로그에는 사용자 입력이나 개인정보가 포함될 수 있으므로 로컬 개발용으로만 사용하고, 운영 환경은 P6Spy를 사용하지 않으며 `LOG_LEVEL_P6SPY=OFF`를 유지하세요. 출력 레벨은 `LOG_LEVEL_P6SPY`로 조정할 수 있습니다.

## API

공통 응답은 `{ "data": ..., "resultCode": "SUCCESS", "resultMessage": "성공" }` 형식입니다. 목록은 `page`(0부터 시작), `size`(기본 20, 최대 100), `regionCode`, `keyword`, 모집공고일 범위(`startDate`, `endDate`; `yyyy-MM-dd`)를 지원합니다.

| 유형 경로 | 공고 테이블 |
|---|---|
| `/api/v1/apt/announcements` | APT 분양 |
| `/api/v1/urban/announcements` | 오피스텔·도시형·민간임대 |
| `/api/v1/remaining/announcements` | APT 잔여세대 |
| `/api/v1/public-rental/announcements` | 공공지원 민간임대 |
| `/api/v1/optional/announcements` | 임의공급 |

각 유형 경로에 `GET /{houseManageNo}/{pblancNo}`를 붙이면 공고 상세와 주택형 목록을 조회합니다.

경쟁률은 각 공고 유형 경로에 `/{houseManageNo}/{pblancNo}/competition-rates`를 붙여 조회합니다. APT 당첨가점은 `/api/v1/apt/announcements/{houseManageNo}/{pblancNo}/scores`, 특별공급 신청현황은 `/api/v1/apt/announcements/{houseManageNo}/{pblancNo}/special-supply-status`에서 조회합니다. 스케줄러가 적재한 해당 테이블의 레코드를 반환합니다.

댓글은 유형 경로 아래에서 조회·등록·수정·삭제합니다.

- `GET /{type}/announcements/{houseManageNo}/{pblancNo}/comments?page=0&size=20`
- `POST /{type}/announcements/{houseManageNo}/{pblancNo}/comments` — 로그인 필요
- `PATCH /{type}/announcements/{houseManageNo}/{pblancNo}/comments/{commentSeq}` — 작성자 로그인 필요
- `DELETE /{type}/announcements/{houseManageNo}/{pblancNo}/comments/{commentSeq}` — 작성자 로그인 필요

`{type}`은 `apt`, `urban`, `remaining`, `public-rental`, `optional` 중 하나입니다. 댓글은 기존 댓글 테이블을 재사용하지 않고 `TB_LTTOT_PBLANC_COMMENT`에 저장합니다. 기존 `T_SUBSCRIPT_COMMENT` 데이터는 보존되지만 새 API에서 노출하지 않습니다. 새 분양 API를 사용하기 전에 `src/main/resources/db/schema/TB_LTTOT_PBLANC_COMMENT.sql`을 DB에 한 번 적용해야 합니다.

회원가입·로그인·이메일 인증 경로는 기존 `/webApi/user/**`, `/webApi/certify/**`를, 게시판 조회는 `/webApi/boards/board-list`를 사용합니다. OpenAPI UI는 `/swagger-ui/index.html`에서 확인할 수 있습니다.
