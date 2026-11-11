# TOADY 프론트엔드 (`toadyReactWeb`)

React 기반의 청약 일정 캘린더 및 분양 정보 웹 애플리케이션입니다. 월별 청약 일정을 데스크톱·모바일 화면에 맞춰 보여주고 공고 상세, 경쟁률, 위치, 댓글을 제공합니다.

## 주요 기능

- 데스크톱 FullCalendar 월간 일정 및 모바일 일정 캘린더
- 공고 유형·지역·접수 일정 필터링
- 전체 기간 공고 자동완성 검색과 선택한 접수 월로 캘린더 이동
- 공고 상세 정보, 경쟁률·당첨 가점, 위치 지도, 댓글 화면
- TanStack Query를 이용한 서버 데이터 캐싱 및 댓글 변경 후 쿼리 갱신

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| UI | React 18, Material UI 5 |
| 개발 서버·빌드 | Vite 5 |
| 서버 상태 | TanStack Query v5 |
| 일정 화면 | FullCalendar |
| 지도 | Kakao Maps JavaScript SDK |
| 웹 서버 | Nginx, Docker |

## 화면 및 코드 구조

```text
src/
├── api/                      # 공통 Fetch 기반 API 클라이언트
├── config/                   # 개발/프로덕션 API 주소 설정
├── features/
│   ├── announcement/          # 공고 API 함수 및 TanStack Query 훅
│   └── auth/                  # 인증 API 및 사용자 상태
├── components/               # 레이아웃, 날짜 선택, 지도 등 공통 UI
├── views/
│   ├── main/                 # 캘린더 메인 화면
│   │   ├── components/        # 공급유형·지역 필터 및 공고 원문 UI
│   │   ├── hooks/             # 자동완성·검색 옵션·목록 필터링
│   │   ├── calendar/          # 데스크톱 및 모바일 캘린더
│   │   ├── subscriptDetail/   # 공고 상세 화면
│   │   ├── subscriptRate/     # 경쟁률·가점 화면
│   │   └── subscriptComment/  # 댓글 화면
│   ├── login/
│   └── signup/
└── utils/                    # 날짜·문자열·SEO 유틸리티
```

## 주요 UX 동작

### 반응형 청약 캘린더

화면 크기에 따라 데스크톱에서는 FullCalendar 월간 뷰를, 모바일에서는 별도 모바일 캘린더를 표시합니다. 공고를 선택하면 상세 패널을 열어 상세 정보와 관련 기능을 확인할 수 있습니다.

### 전체 기간 공고 검색

검색 입력은 2자 이상일 때 300ms 디바운스로 `/api/v1/announcements/suggestions`를 호출합니다. 검색 결과에는 접수 월과 주소 정보를 함께 표시합니다. 과거 공고를 선택하면 `rceptBgnde` 기준 월로 캘린더를 이동하고 해당 공고 상세 화면을 엽니다. 지역·현재 월의 공고 검색 옵션도 같은 입력 UI에서 제공합니다.

### 서버 상태 캐싱

서버 데이터를 컴포넌트 로컬 상태와 분리하고, 동일 조건의 조회 결과를 재사용해 화면 이동 시 불필요한 네트워크 요청을 줄이기 위해 TanStack Query를 사용합니다. 공고 목록 조회의 `staleTime`은 3분이며, 상세·경쟁률 등 다른 조회는 훅별 설정을 사용합니다. 댓글 작성·수정·삭제 후에는 관련 쿼리를 무효화해 최신 데이터를 다시 가져옵니다.

## 로컬 실행

필요 환경: Node.js 22, npm. 의존성 잠금 파일은 `package-lock.json`으로 통일합니다.

```bash
cp .env.example .env.local
npm ci
npm run dev
```

Vite 개발 서버는 `http://localhost:3000`에서 실행되며 `/api`와 `/webApi` 요청을 `http://localhost:8081`의 로컬 API 서버로 프록시합니다. 백엔드 API가 실행 중이어야 공고·인증 기능을 사용할 수 있습니다.

```bash
# 프로덕션 빌드
npm run build

# 빌드 결과 미리보기
npm run preview
```

## API 및 배포 설정

개발 환경에서는 Vite 프록시를 사용하고, 프로덕션 환경에서는 `src/config/index.js`에 설정된 `https://api.toady.co.kr`를 API 주소로 사용합니다. 별도 API 도메인을 호출하므로 운영 환경의 DNS, TLS, Nginx 라우팅 및 API CORS 허용 Origin 설정이 서로 일치해야 합니다.

지도 기능은 `.env.local`의 `VITE_KAKAO_MAP_KEY`에 본인의 Kakao JavaScript 키를 설정하면 사용할 수 있습니다. `src/config/loadKakaoMaps.js`가 SDK를 로드합니다. 키가 없어도 캘린더 화면과 빌드는 사용할 수 있으며 지도 기능에는 키가 필요합니다.

`VITE_*` 값은 빌드 시 브라우저 번들에 포함됩니다. JavaScript 키는 서버 비밀키가 아니며 Kakao 개발자 콘솔에 허용 도메인을 등록해야 합니다. DB 비밀번호·JWT 비밀키·서버용 API 키는 이곳에 넣지 않습니다.

Docker 빌드 시 `--build-arg VITE_KAKAO_MAP_KEY`로 전달합니다. CI 배포는 GitHub Actions의 `VITE_KAKAO_MAP_KEY` Secret을 사용하며, 누락되면 이미지 생성 전에 중단합니다. 정적 앱이므로 실행 중인 컨테이너의 환경변수만 바꾸어서는 키가 변경되지 않고 다시 빌드해야 합니다.

Dockerfile은 Node.js 이미지에서 Vite 정적 산출물을 만든 뒤 Nginx 이미지에서 서빙하는 멀티스테이지 빌드입니다. `nginx.conf`에는 SPA 라우팅 fallback과 정적 파일 캐시 헤더, Gzip 압축을 설정해 반복 방문 시 리소스 재사용을 돕고 텍스트 기반 에셋의 전송량을 줄이도록 했습니다.
