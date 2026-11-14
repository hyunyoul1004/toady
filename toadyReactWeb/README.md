# TOADY 사용자 화면 (`toadyReactWeb`)

청약 캘린더부터 공고 상세·실거래 지도·커뮤니티·나의 청약까지 TOADY의 모든 사용자 화면을 담은 React 앱입니다. 데스크톱과 모바일을 함께 지원하고, 팝업과 상세 화면을 브라우저 히스토리와 맞춰 뒤로가기·앞으로가기·새로고침에서 같은 화면이 복원되게 했습니다.

## 화면

| 경로 | 화면 |
| --- | --- |
| `/` | 청약 캘린더(데스크톱 FullCalendar · 모바일 전용 달력 · 목록 보기), 지역·유형 필터, 전체 기간 자동완성 |
| `/?subscriptSeq=…&tabType=…` | 공고 상세(청약 정보 · 경쟁률 · 위치 · 이야기), 상세는 오른쪽 패널로 열림 |
| `/this-week`, `/regions` | 이번 주 청약(접수·당첨자 발표·다음 주 시작), 지역별 청약 |
| `/match`, `/match/zupzup` | 조건으로 넣을 수 있는 공고 찾기, 무순위·잔여세대 모음 |
| `/score` | 청약 가점 계산기와 생활권별 공개 당첨가점 비교 |
| `/my` | 나의 청약(관심 공고 · 진행 단계 · 메모 · 비교 · 캘린더 연결 · 알림 설정) |
| `/account` | 내 정보(닉네임 · 비밀번호 · 프로필 캐릭터 · 받은 답글 · 내가 쓴 이야기 · 탈퇴) |
| `/faq`, `/privacy`, `/terms` | 자주 묻는 질문, 개인정보처리방침, 이용약관 |

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| UI | React 18, MUI 5, FullCalendar 6 |
| 라우팅·상태 | React Router 6, TanStack Query v5(서버 상태), Zustand(인증 상태) |
| 지도 | Kakao Maps JavaScript SDK(지도·주소 검색·장소 검색) |
| 빌드·서빙 | Vite 5, Nginx(멀티스테이지 Docker 이미지) |
| 테스트 | Node test runner, JSDOM |

## 구조

```text
src/
├── api/               # fetch 기반 API 클라이언트(토큰 갱신·401 처리)
├── routes/            # 화면 경로
├── hooks/             # useHistoryOverlay(팝업=히스토리 레이어), useDirectDetailHistory
├── components/
│   ├── layout/        # 사이드바·모바일 탭바·로그인 창
│   └── kakao/         # Kakaomap: 위치 탭 지도(전체 화면·말풍선·검색·자동완성)
├── views/main/        # 캘린더 메인 · 공고 상세 · 경쟁률 · 이야기
├── features/
│   ├── announcement/  # 공고 API·훅, location/(위치 판정·실거래 지도 규칙·거리·반경 도구), export/(이미지 저장)
│   ├── planner/       # 나의 청약(관심 공고·단계·메모·비교·캘린더)
│   ├── match/         # 맞춤 청약·분양가 vs 주변 시세·자금 계획
│   ├── score/         # 가점 계산기
│   ├── account/       # 내 정보·프로필 캐릭터·받은 답글
│   ├── explore/       # 이번 주·지역별 청약
│   ├── push/          # 웹 푸시 구독
│   ├── analytics/     # 익명 페이지 조회·행동 기록
│   └── auth/, story/, faq/, intro/, legal/
└── utils/             # 날짜·히스토리 레이어·공유·SEO
```

## 구현 포인트

### 팝업과 히스토리
로그인 창·원문·이미지 저장·단지 선택·전체 화면 지도 같은 팝업을 `location.state.overlayLayers`의 레이어로 다룹니다(`useHistoryOverlay`). 맨 위 레이어를 닫으면 뒤로가기, 아래 레이어를 닫으면 위 레이어까지 함께 걷어 내므로 닫은 화면이 뒤로가기로 다시 열리지 않습니다. 공유 링크로 상세에 바로 들어와도 첫 뒤로가기가 목록으로 가도록 히스토리를 한 칸 끼워 넣습니다(`useDirectDetailHistory`). 판단 규칙은 순수 함수(`utils/overlay.util.js`)로 두고 테스트합니다.

### 위치 탭 실거래 지도
- **위치 판정**: 정확한 지번·도로명이고 시군구가 일치할 때만 확정 위치로 핀을 놓고, 단지명 검색은 후보, 지구·블록 주소는 법정동 일대(점선 원)로만 보여 줍니다(`resolveLocation.js`).
- **단지 말풍선**: 화면 범위 근처의 단지만 만들고, 넓게 보면 법정동·시군구 묶음으로 바꿉니다. 지도를 옮겨 다른 시군구가 보이면 그 지역 거래도 불러옵니다.
- **전체 화면**: 지도를 다시 만들지 않고 지도 DOM만 `document.body`로 옮기는 포털 방식이라 위치·확대 수준이 유지됩니다. PC는 옆 패널, 모바일은 아래 시트로 단지 정보를 보여 줍니다.
- **도구**: 오른쪽 세로 막대의 확대·축소, 거리 재기(누른 곳에서 출발, 마우스 미리보기), 반경 그리기(끌기 또는 두 번 누르기, 마우스·터치 공통, 반경 안 중간 거래가와 이 공고 평당 분양가 비교).
- **검색**: 단지명·법정동·분양 공고는 불러온 자료에서 바로, 주소·장소는 카카오에서 찾아 자동완성합니다. 번지까지 맞는 주소만 확정 위치로, 동·도로·장소는 그 일대로 지도만 옮기고 '○○ 일대 보는 중' 표시를 남깁니다.

### 인증 상태
토큰은 요청 당시 값과 비교해 갱신하므로, 로그아웃 뒤 늦게 도착한 응답이 로그인 상태를 되살리지 않습니다. 다른 탭의 로그인·로그아웃은 `storage` 이벤트로 맞추고, 계정이 바뀌면 진행 중인 조회를 취소하고 캐시를 비웁니다.

### 데이터를 다루는 방식
- 서버 상태는 TanStack Query로 캐시하고, 댓글 변경 뒤 관련 목록·상세 캐시를 무효화합니다.
- 비회원 관심 공고·가점·검색 조건·보기 방식은 브라우저에 저장하고, 회원은 서버에 저장합니다.
- 외부 HTML을 삽입하지 않고, 지도 말풍선의 단지명도 `textContent`로만 넣습니다.
- 익명 페이지 조회·행동 기록은 인증 헤더 없이 보내며 회원 ID·검색어·URL 전체는 보내지 않습니다.

## 실행

필요 환경: Node.js 22, npm(잠금 파일은 `package-lock.json`).

```bash
cp .env.example .env.local     # VITE_KAKAO_MAP_KEY: 본인 Kakao JavaScript 키(지도 기능에만 필요)
npm ci
npm run dev                    # http://localhost:3000, /api·/webApi는 localhost:8081로 프록시
npm test                       # 회귀 테스트(146개)
npm run build
```

- 운영 빌드는 API 주소로 `https://api.toady.co.kr`를 씁니다(`src/config/index.js`).
- `VITE_*` 값은 브라우저 번들에 들어가는 공개 값입니다. Kakao 개발자 콘솔에 허용 도메인을 등록하고, 서버 비밀값은 넣지 않습니다.
- Docker 이미지는 `--build-arg VITE_KAKAO_MAP_KEY`로 키를 받아 빌드합니다. 정적 앱이라 키를 바꾸려면 다시 빌드해야 합니다.
- `nginx.conf`는 SPA 라우팅, 정적 파일 캐시, Gzip, 공고·생활권 공개 HTML과 사이트맵의 API 연결을 담당합니다. `/sw.js`·`/manifest.webmanifest`는 장기 캐시하지 않습니다.

## 테스트

| 파일 | 내용 |
| --- | --- |
| `planner-navigation`, `search-return` | JSDOM에 실제 App·라우터·Query를 띄우고 API 응답만 대체해 목록 ↔ 나의 청약 ↔ 캘린더 왕복, 새로고침 복원, 검색 조건 유지 확인 |
| `overlay-date-rules`, `stage` | 히스토리 레이어 규칙, 나의 청약 단계별 다음 할 일·입주까지 남은 기간 |
| `announcement-auth` | 탭 간 계정 변경, 늦은 응답 차단, 토큰 갱신 경합 |
| `location`, `price-map` | 위치 확정·후보 판정, 지도 묶음·반경 요약·자동완성 후보·검색 이동 |
| `planner`, `match`, `research`, `score-story` 등 | 일정 중복 정리, 맞춤 청약 판정, 시세 비교, 가점 계산 |

실제 브라우저 배치와 모바일 화면은 이 테스트와 별도로 브라우저에서 확인합니다.
