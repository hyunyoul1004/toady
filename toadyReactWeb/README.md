# toadyReactWeb

TOADY의 사용자 화면입니다. 청약 캘린더부터 공고 상세, 실거래 지도, 이야기, 쪽지, 나의 청약까지 전부 이 앱에 있습니다. PC와 모바일을 같이 지원하고, 팝업과 상세 화면을 브라우저 히스토리와 맞춰서 뒤로가기, 앞으로가기, 새로고침 때 같은 화면이 돌아오게 했습니다.

React 18, MUI 5, Vite 5, TanStack Query v5, Zustand, React Router 6, FullCalendar 6, Kakao Maps SDK

## 화면

| 경로 | 화면 |
| --- | --- |
| `/` | 청약 캘린더(PC는 FullCalendar, 모바일은 전용 달력과 목록), 지역·유형 필터, 자동완성 검색 |
| `/?subscriptSeq=…&tabType=…` | 공고 상세(청약 정보, 경쟁률, 위치, 이야기). PC에서는 오른쪽 패널로 열림 |
| `/announcements/{유형}/{관리번호}/{공고번호}` | 공유·검색엔진용 공고 주소. 앱에서는 위 상세 화면으로 바로 열림 |
| `/this-week`, `/regions` | 이번 주 청약, 지역별 청약 |
| `/match`, `/match/zupzup` | 조건에 맞는 공고 찾기, 무순위·잔여세대 모음 |
| `/score` | 가점 계산기, 생활권별 공개 당첨가점 비교 |
| `/my` | 나의 청약(관심 공고, 진행 단계, 메모, 비교, 캘린더 연결, 알림 설정) |
| `/messages` | 쪽지함, 대화, 받는 범위 설정 |
| `/account` | 내 정보(닉네임, 비밀번호, 프로필 캐릭터, 받은 답글, 내가 쓴 이야기, 탈퇴) |
| `/faq`, `/privacy`, `/terms` | 자주 묻는 질문, 개인정보처리방침, 이용약관 |

예전 주소(`/areas`, `/weekly`, `/zupzup`, `/help/faq`, `/living-areas`)는 지금 화면으로 넘깁니다.

## 폴더

```text
src/
├── api/              fetch 기반 API 클라이언트(토큰 갱신, 401 처리)
├── routes/           화면 경로, 예전 주소 넘기기
├── hooks/            useHistoryOverlay(팝업 = 히스토리 레이어), useDirectDetailHistory
├── components/
│   ├── layout/       사이드바, 모바일 탭바, 로그인 창
│   └── kakao/        위치 탭 지도(전체 화면, 말풍선, 검색)
├── views/main/       캘린더 메인, 공고 상세, 경쟁률, 이야기
├── features/
│   ├── announcement/ 공고 API·훅, location/(위치 판정, 지도 규칙, 거리·반경 도구), export/(이미지 저장)
│   ├── planner/      나의 청약
│   ├── match/        맞춤 청약, 분양가와 주변 시세, 자금 계획
│   ├── community/    쪽지, 차단, 신고, 프로필 카드
│   ├── score/        가점 계산기
│   ├── account/      내 정보, 프로필 캐릭터, 받은 답글, 사이트 공지 띠
│   ├── explore/      이번 주, 지역별 청약
│   ├── push/         웹 푸시 구독
│   ├── analytics/    익명 페이지 조회, 행동 기록, 검색어 기록
│   └── auth/, story/, faq/, intro/, legal/
└── utils/            날짜, 히스토리 레이어, 공유, SEO
```

## 구현하면서 정한 것들

### 팝업과 히스토리
로그인 창, 원문, 이미지 저장, 단지 선택, 전체 화면 지도 같은 팝업을 `location.state.overlayLayers`에 쌓인 레이어로 다룹니다(`useHistoryOverlay`). 맨 위 레이어를 닫으면 뒤로가기와 같고, 아래 레이어를 닫으면 위에 쌓인 것까지 같이 걷어 냅니다. 그래서 닫은 화면이 뒤로가기로 다시 열리지 않습니다. 공유 링크로 상세에 바로 들어와도 첫 뒤로가기가 목록으로 가도록 히스토리를 한 칸 끼워 넣습니다(`useDirectDetailHistory`). 판단 규칙은 순수 함수(`utils/overlay.util.js`)로 빼서 테스트합니다.

### 위치 탭 실거래 지도
- 위치 판정: 지번이나 도로명이 정확하고 시군구까지 맞을 때만 확정 핀을 놓습니다. 단지명으로 찾은 건 후보, 지구·블록 주소는 법정동 일대(점선 원)로만 보여 줍니다(`resolveLocation.js`). 관리자가 확인해 넣은 주소가 있으면 그걸 먼저 씁니다.
- 말풍선: 화면 근처 단지만 그리고, 넓게 보면 법정동·시군구 묶음으로 바꿉니다. 지도를 옮겨 다른 시군구가 보이면 그 지역 거래도 불러옵니다.
- 전체 화면: 지도를 새로 만들지 않고 지도 DOM만 `document.body`로 옮겨서 위치와 확대 수준이 그대로 유지됩니다. PC는 옆 패널, 모바일은 아래 시트로 단지 정보를 보여 줍니다.
- 도구: 확대·축소, 거리 재기, 반경 그리기(끌거나 두 번 눌러서, 마우스와 터치 모두). 반경 안 중간 거래가와 이 공고의 평당 분양가를 비교해 줍니다.
- 검색: 단지명·법정동·분양 공고는 불러온 자료에서 바로 찾고, 주소·장소는 카카오에서 찾습니다. 번지까지 맞는 주소만 확정 위치로 쓰고, 동이나 장소는 그 일대로 지도만 옮깁니다.

### 로그인 상태
토큰은 요청을 보낸 당시 값과 비교해서 갱신합니다. 로그아웃 뒤에 늦게 도착한 응답이 로그인 상태를 되살리지 않게 하려는 것입니다. 다른 탭에서 로그인·로그아웃하면 `storage` 이벤트로 맞추고, 계정이 바뀌면 진행 중인 조회를 취소하고 캐시를 비웁니다.

### 데이터
- 서버 데이터는 TanStack Query로 캐시하고, 이야기를 쓰거나 지우면 관련 목록과 상세 캐시를 무효화합니다.
- 비회원의 관심 공고, 가점, 검색 조건, 보기 방식은 브라우저에 두고, 회원은 서버에 둡니다.
- 외부 HTML은 화면에 넣지 않고, 지도 말풍선의 단지명도 `textContent`로만 넣습니다.
- 익명 측정은 인증 헤더 없이 보내고, 회원 ID나 전체 URL은 보내지 않습니다.

## 실행

Node.js 22가 필요합니다(잠금 파일은 `package-lock.json`).

```bash
cp .env.example .env.local   # VITE_KAKAO_MAP_KEY: 카카오 JavaScript 키(지도에만 필요)
npm ci
npm run dev                  # http://localhost:3000, /api·/webApi는 localhost:8081로 넘김
npm test                     # 164개
npm run build
```

- 운영 빌드는 API 주소로 `https://api.toady.co.kr`를 씁니다(`src/config/index.js`).
- `VITE_*` 값은 브라우저 번들에 그대로 들어갑니다. 카카오 개발자 콘솔에 허용 도메인을 등록하고, 서버 비밀값은 넣지 않습니다.
- Docker 이미지는 `--build-arg VITE_KAKAO_MAP_KEY`로 키를 받아 빌드합니다. 키를 바꾸면 이미지를 다시 빌드해야 합니다.
- 컨테이너 안의 `nginx.conf`가 SPA 라우팅, 정적 파일 캐시, gzip을 맡습니다. 공고·생활권 같은 공유 주소는 사람에게는 앱을, 검색·공유 봇에게는 API가 만든 서버 HTML을 줍니다(User-Agent로 구분).

## 테스트

| 파일 | 확인하는 것 |
| --- | --- |
| `planner-navigation`, `search-return` | JSDOM에 실제 App·라우터·Query를 띄우고 API 응답만 바꿔서, 목록과 나의 청약, 캘린더를 오갈 때 화면 복원과 검색 조건 유지 |
| `overlay-date-rules`, `stage` | 히스토리 레이어 규칙, 진행 단계별 다음 할 일과 입주까지 남은 기간 |
| `announcement-auth` | 탭 사이 계정 변경, 늦은 응답 무시, 토큰 갱신 경합 |
| `location`, `price-map` | 위치 확정·후보 판정, 지도 묶음, 반경 요약, 검색 이동 |
| `planner`, `match`, `research`, `score-story` 등 | 일정 정리, 맞춤 청약 판정, 시세 비교, 가점 계산 |

실제 브라우저에서의 배치나 모바일 화면은 이 테스트와 별개로 브라우저에서 직접 확인합니다.
