# Toady API 프론트엔드 연동 안내

프론트엔드 구현용 API 계약과 JSON 예시입니다. API는 공통 응답 래퍼를 사용하며, 실제 공고 필드 값은 DB의 공고에 따라 달라집니다.

## 접속 정보

| 환경 | Base URL |
|---|---|
| 로컬 | `http://localhost:8081` |
| 운영 | `https://api.toady.co.kr` |

- Swagger UI: `{BASE_URL}/swagger-ui/index.html`
- OpenAPI JSON: `{BASE_URL}/v3/api-docs`
- 요청/응답 콘텐츠 타입: `application/json; charset=UTF-8`
- 시간 문자열은 ISO 형식(예: `2026-10-30T12:00:00`)이며, 공고일 문자열은 원본 데이터 형식에 따라 `yyyyMMdd` 또는 `yyyy-MM-dd`일 수 있습니다.
- 일반 공고 조회는 인증이 필요 없습니다. 댓글 작성·수정·삭제는 로그인 후 `Authorization: Bearer {accessToken}` 헤더가 필요합니다.

## 공통 응답

목록/조회 성공:

```json
{
  "data": {},
  "resultCode": "SUCCESS",
  "resultMessage": "성공"
}
```

검증/업무 오류는 API에 따라 HTTP 400 또는 HTTP 200으로 반환될 수 있습니다. 응답의 `resultCode`와 `resultMessage`를 확인하고, 성공 코드는 `SUCCESS`입니다.

## 분양 공고 통합 목록

`GET /api/v1/announcements`

아파트, 오피스텔·도시형·민간임대, 잔여세대, 공공지원 민간임대, 임의공급을 한 목록으로 조회합니다. 결과는 청약접수 시작일 최신순이며, 페이지는 0부터 시작합니다. `types`를 생략하면 전체 유형입니다. `rceptBgnde`에는 청약접수 시작일을 반환합니다.
통합 목록에는 청약접수 종료일(`rceptEndde`)과 APT의 1·2순위 해당지역·경기지역·기타지역 접수 시작/종료일 필드(`gnrlRnk1...`, `gnrlRnk2...`)도 포함되며, APT가 아닌 유형의 순위별 값은 `null`입니다.

| 쿼리 | 필수 | 설명 |
|---|---:|---|
| `types` | 아니오 | `apt`, `urban`, `remaining`, `public-rental`, `optional`. 쉼표 구분 또는 반복 파라미터를 허용하며 미지정 시 전체 유형 |
| `regionCode` | 아니오 | 공급지역코드 |
| `keyword` | 아니오 | 주택명 검색어 |
| `startDate` | 아니오 | 청약접수 시작일의 조회 시작일, `yyyy-MM-dd` |
| `endDate` | 아니오 | 청약접수 시작일의 조회 종료일, `yyyy-MM-dd` |
| `page` | 아니오 | 0부터 시작, 기본값 `0` |
| `size` | 아니오 | 페이지 크기, 기본값 `20`, 허용 범위 `1`~`100` |

```http
GET /api/v1/announcements?page=0&size=20
GET /api/v1/announcements?types=apt&page=0&size=20
GET /api/v1/announcements?types=apt,urban&page=0&size=20
GET /api/v1/announcements?types=apt&types=urban&page=0&size=20
GET /api/v1/announcements?regionCode=011&keyword=자이&startDate=2026-10-01&endDate=2026-10-31
```

실제 응답 예시:

```json
{
  "data": {
    "content": [
      {
        "type": "optional",
        "houseManageNo": "2026940234",
        "pblancNo": "2026940234",
        "houseNm": "엘리프 성성호수공원 1BL",
        "houseSecd": "05",
        "houseSecdNm": "민영",
        "regionCode": "312",
        "regionName": "충남",
        "hssplyAdres": "충청남도 천안시 ...",
        "hssplyZip": "31100",
        "rceptBgnde": "20261028",
        "totalSupplyHouseholds": "5"
      }
    ],
    "page": 0,
    "size": 1,
    "totalElements": 6161,
    "totalPages": 6161
  },
  "resultCode": "SUCCESS",
  "resultMessage": "성공"
}
```

페이지 이동은 다음 페이지 요청에 `page=1`, 그다음 `page=2`처럼 증가시킵니다. 한 페이지의 `content`에는 선택한 유형만 들어가며, 항목의 `type` 값으로 상세 조회 경로를 구성합니다.

```javascript
const params = new URLSearchParams({
  page: "0",
  size: "20",
  types: "apt,urban"
});
const response = await fetch(`${API_BASE_URL}/api/v1/announcements?${params}`);
const result = await response.json();
```

## 공고 상세

`GET /api/v1/{type}/announcements/{houseManageNo}/{pblancNo}`

`type`은 `apt`, `urban`, `remaining`, `public-rental`, `optional` 중 하나입니다. 통합 목록의 항목을 선택하면 그 항목의 `type`, `houseManageNo`, `pblancNo`를 그대로 경로에 사용합니다.

응답 `data`는 `announcement`(유형별 원본 공고 엔티티), `models`(주택형 배열), `commentCount`로 구성됩니다. 유형마다 원본 공고/주택형 필드는 다를 수 있습니다.

```json
{
  "data": {
    "announcement": {
      "houseManageNo": "2026940234",
      "pblancNo": "2026940234",
      "houseNm": "엘리프 성성호수공원 1BL",
      "houseSecdNm": "임의공급",
      "subscrptAreaCodeNm": "충남",
      "hssplyAdres": "충청남도 천안시 서북구 업성동 업성2구역 1BL",
      "totSuplyHshldco": "5",
      "rcritPblancDe": "20261028",
      "przwnerPresnatnDe": "20261104",
      "pblancUrl": "https://www.applyhome.co.kr/..."
    },
    "models": [
      {
        "houseManageNo": "2026940234",
        "pblancNo": "2026940234",
        "modelNo": "01",
        "houseTy": "111.1359",
        "suplyHshldco": "5",
        "lttotTopAmount": "79,940",
        "regDt": "2026-10-29T17:51:09"
      }
    ],
    "commentCount": 0
  },
  "resultCode": "SUCCESS",
  "resultMessage": "성공"
}
```

## 경쟁률·당첨가점·특별공급 현황

공고별 경쟁률은 스케줄러 적재 테이블에서 조회합니다. 결과는 `data` 배열이며 테이블 컬럼명에 대응하는 camelCase 필드로 반환합니다.

| 메서드 | 경로 | 테이블 |
|---|---|---|
| GET | `/api/v1/apt/announcements/{houseManageNo}/{pblancNo}/competition-rates` | `TB_APT_LTTOT_PBLANC_CMPET` |
| GET | `/api/v1/urban/announcements/{houseManageNo}/{pblancNo}/competition-rates` | `TB_URBTY_OFCTL_LTTOT_PBLANC_CMPET` |
| GET | `/api/v1/public-rental/announcements/{houseManageNo}/{pblancNo}/competition-rates` | `TB_PBL_PVT_RENT_LTTOT_PBLANC_CMPET` |
| GET | `/api/v1/cancel-resupply/announcements/{houseManageNo}/{pblancNo}/competition-rates` | `TB_CANC_RESPL_LTTOT_PBLANC_CMPET` |
| GET | `/api/v1/remaining/announcements/{houseManageNo}/{pblancNo}/competition-rates` | `TB_REMNDR_LTTOT_PBLANC_CMPET` |
| GET | `/api/v1/optional/announcements/{houseManageNo}/{pblancNo}/competition-rates` | `TB_OPT_LTTOT_PBLANC_CMPET` |
| GET | `/api/v1/apt/announcements/{houseManageNo}/{pblancNo}/scores` | `TB_APT_LTTOT_PBLANC_SCORE` |
| GET | `/api/v1/apt/announcements/{houseManageNo}/{pblancNo}/special-supply-status` | `TB_APT_SPSPLY_REQST_STUS` |

경쟁률 API 응답의 속성은 유형별 테이블에 따라 달라집니다. 당첨가점 응답에는 `lwetScore`, `topScore`, `avrgScore`가 포함되고, 특별공급 현황 응답에는 주택형별 배정세대수와 지역별 접수건수가 포함됩니다.

## 공고 댓글

공통 경로:

`/api/v1/{type}/announcements/{houseManageNo}/{pblancNo}/comments`

| 메서드 | 경로 | 인증 | 설명 |
|---|---|---:|---|
| `GET` | 댓글 경로 `?page=0&size=20` | 불필요 | 댓글 페이지 조회 |
| `GET` | 댓글 경로 `/{commentSeq}` | 불필요 | 댓글 1건 조회 |
| `POST` | 댓글 경로 | 필요 | 댓글/답글 작성 |
| `PATCH` | 댓글 경로 `/{commentSeq}` | 필요 | 작성자 본인 댓글 수정 |
| `DELETE` | 댓글 경로 `/{commentSeq}` | 필요 | 작성자 본인 댓글 삭제 |

작성 요청:

```json
{
  "parentCommentSeq": null,
  "content": "관심 있는 공고라서 정보 공유합니다."
}
```

답글은 `parentCommentSeq`에 부모 댓글 번호를 전달합니다. `content`는 공백일 수 없고 최대 4,000자입니다.

수정 요청:

```json
{
  "content": "수정한 댓글 내용입니다."
}
```

댓글 조회 및 작성 성공 응답의 `data` 예시:

```json
{
  "commentSeq": 1,
  "announcementType": "apt",
  "houseManageNo": "2025000123",
  "pblancNo": "2025000123",
  "parentCommentSeq": null,
  "content": "관심 있는 공고라서 정보 공유합니다.",
  "recommend": 0,
  "oppose": 0,
  "deleteYn": "N",
  "createUserSeq": 100,
  "userNicName": "toady",
  "regDt": "2026-10-30T12:00:00",
  "updateUserSeq": null,
  "updateDt": null
}
```

댓글 목록은 공통 페이지 응답 구조(`content`, `page`, `size`, `totalElements`, `totalPages`)를 사용합니다.

## 계정 및 이메일 인증

| 메서드 | 경로 | 요청 JSON |
|---|---|---|
| `POST` | `/webApi/user/login` | `{"userEmail":"user@example.com","userPassword":"..."}` |
| `POST` | `/webApi/user/regUser` | `{"userEmail":"user@example.com","userPassword":"...","userPasswordConfirm":"...","userNicName":"nickname"}` |
| `POST` | `/webApi/certify/certify-check` | `{"userEmail":"user@example.com","certifyCode":"..."}` |
| `POST` | `/webApi/certify/find-password-request-certify` | `{"userEmail":"user@example.com"}` |
| `POST` | `/webApi/certify/certify-confrim-and-password-change` | `{"userEmail":"user@example.com","userPassword":"...","userPasswordConfirm":"...","certifyCode":"..."}` |

로그인 성공 응답:

```json
{
  "data": {
    "accessToken": "<JWT>"
  },
  "resultCode": "SUCCESS",
  "resultMessage": "로그인 성공"
}
```

인증이 필요한 댓글 요청에는 토큰을 다음과 같이 사용합니다.

```http
Authorization: Bearer <JWT>
```

회원가입 및 인증 코드는 이메일로 발송됩니다. 비밀번호는 6~20자이며 영문과 숫자 또는 특수문자를 포함해야 합니다. 비밀번호 찾기 인증코드는 이메일로 받고 확인 후 비밀번호를 변경합니다.

## 레거시 유형별 공고 목록

`GET /api/v1/{type}/announcements` 경로는 하위 호환을 위해 유지되지만 Swagger에는 노출하지 않습니다. 프론트엔드 신규 연동은 유형 선택이 가능한 `GET /api/v1/announcements?types=...` 사용을 권장합니다.
