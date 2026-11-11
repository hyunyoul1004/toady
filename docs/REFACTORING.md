# 구조와 배포 검증

## 프론트엔드

`Main.jsx`는 캘린더 조회와 상세 패널을 연결합니다.

- `announcementFilters.js`: 공급지역, 공급유형, 지역 검색 옵션 정의
- `hooks/useAnnouncementSearch.js`: 전체 기간 자동완성, 검색 옵션 조합, 목록 필터링
- `components/AnnouncementFilters.jsx`: 공급유형·지역·검색 UI 및 토글
- `components/AnnouncementNotice.jsx`: 공고 원문과 유형별 상세 표시

검색어가 바뀌거나 화면이 해제되면 이전 자동완성 요청의 응답을 무시합니다.
기존 URL 파라미터, 상세 탭, 캘린더 이동과 API 응답 형식은 유지합니다.

## API

`AnnouncementService`는 기존 호출 계약을 유지하는 진입점입니다.
검색은 `AnnouncementSearchService`, 상세·존재 확인은 `AnnouncementDetailService`,
경쟁률·가점·특별공급은 `AnnouncementCompetitionService`가 담당합니다.
유형별 통합 DTO 변환은 `AnnouncementListMapper`에 모았습니다.

현재 통합 검색의 메모리 병합 방식은 유지합니다. 깊은 페이지의 후보 조회량이
늘어나는 특성도 남아 있습니다. 실제 데이터량·응답 시간을 측정한 다음 공통 조회
테이블 또는 DB 통합 쿼리로 바꾸며, 이번 변경에서 테이블 구조를 추정해 바꾸지 않습니다.

## CI와 배포

PR과 master push에서 API 테스트, 수집기 테스트, 프론트 빌드를 실행합니다.
외부 API·실제 DB 적재 통합 테스트는 기존처럼 명시적으로 활성화해야 합니다.
master 배포는 검증 성공 후에만 진행하며 세 이미지 모두 커밋 SHA로 태깅합니다.
Compose의 `DOCKER_USERNAME`, `IMAGE_TAG`가 배포할 이미지 집합을 지정합니다.

배포 후 `scripts/check-deployment.sh`는 다음을 확인합니다.

- API `/internal/health`의 성공 응답과 DB 연결
- 웹 컨테이너의 정적 페이지 HTTP 응답
- 세 앱 컨테이너의 실행·재시작 상태

이 확인은 외부 DNS·TLS·Nginx 라우팅이나 수집 작업의 완료를 검증하지 않습니다.
무중단 배포와 자동 롤백을 제공하지 않으며, 검증 실패 시 workflow가 실패합니다.
서버에는 기존 Docker Compose와 함께 `bash`, `curl`이 필요합니다.

마지막으로 검증에 성공한 커밋은 서버 `~/toady/.deployed-image-tag`에 기록됩니다.
기존 `latest` 기본값은 수동 실행 호환용입니다. 새 CI는 `latest`를 갱신하지 않습니다.
수동 재배포도 반드시 태그를 지정합니다.

```bash
cd ~/toady
export DOCKER_USERNAME=dukkeoba
export IMAGE_TAG=배포할_커밋_SHA
docker-compose -p toady pull toadyWebApi toadyScheduler toadyReactWeb
docker-compose -p toady up -d toadyWebApi toadyScheduler toadyReactWeb
bash scripts/check-deployment.sh
```

이전 커밋 태그를 사용하면 앱 이미지를 롤백할 수 있습니다. 실패한 배포를 조사할 때
마지막 성공 태그와 현재 컨테이너의 이미지가 다를 수 있다는 점을 확인합니다.
배포 중 실패한 컨테이너도 기록을 위해 남겨 두며 이미지를 자동 삭제하지 않습니다.
DB 스키마 변경 절차는 [DB 변경 관리](../db/README.md)를 따릅니다.

배포 전 지도용 `VITE_KAKAO_MAP_KEY` GitHub Actions Secret을 설정합니다. 브라우저용 빌드 설정과 저장소 파일 정책은 [저장소 관리 안내](REPOSITORY.md)를 참고하세요.
