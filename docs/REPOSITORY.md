# 저장소 관리

## 추적하는 파일

소스, 테스트, README·설계 문서, CI 및 Docker 설정, 버전별 SQL,
Gradle Wrapper(`gradle-wrapper.jar` 포함), npm의 `package-lock.json`을 유지합니다.
빌드 재현에 필요한 Wrapper와 잠금 파일을 생성물이라는 이유로 제외하지 않습니다.
공개한 기본 Spring 설정은 실제 값 대신 환경변수를 참조합니다.

## 제외하는 파일

루트 `.gitignore`는 모든 앱에 공통으로 적용됩니다.

- 실제 `.env`, `.env.local`, `.env.prod`, `.env.migrations` 등과 로컬 Spring 설정
- 개인키·인증서 저장소, 로컬 AWS·SSH 설정
- `node_modules`, Gradle 캐시, 빌드 결과, 테스트 리포트, 로그와 프로세스 덤프
- IDE·OS 설정과 DB 백업
- npm과 중복되는 프론트 `yarn.lock`, 참조되지 않는 기존 폰트 디렉터리 3개

폰트와 Yarn 잠금 파일은 로컬 사본을 유지하고 Git 추적에서만 제외합니다.
새 폰트가 필요하면 실제 사용하는 파일과 배포 라이선스를 함께 추가해야 합니다.
`.env.example`, `.env.*.example`은 자리표시자만 담아 공개할 수 있습니다.
SQL 전체를 무시하지 않으므로 스키마와 마이그레이션 파일은 계속 추적됩니다.

## 커밋 전 확인

저장소 루트에서 실행합니다.

```bash
python3 scripts/check-repository.py
git diff --check
git status --short
git diff --cached --stat
```

`check-repository.py`는 이미 추적된 파일도 검사합니다. `.gitignore` 추가만으로 기존
추적 파일이 제외되지는 않기 때문입니다. 같은 검사를 PR·master CI에서도 실행합니다.
이 검사는 파일 정책과 일부 식별 가능한 키 형식을 확인하며 모든 비밀값 탐지를 보장하지는 않습니다.
스테이징된 diff도 직접 확인합니다.

## 지도 설정과 배포

- 로컬은 `toadyReactWeb/.env.local`에 `VITE_KAKAO_MAP_KEY`를 설정합니다.
- 배포 전에 GitHub Actions Secret `VITE_KAKAO_MAP_KEY`를 등록합니다.
- CI는 이 값을 Docker 빌드 인자로 전달합니다. 누락 시 배포 이미지 생성 전에 실패합니다.
- Kakao JavaScript 키는 최종 브라우저 번들에 공개되는 값입니다. 허용 도메인을
  Kakao 개발자 콘솔에 등록하고 서버 비밀키와 구분합니다.
- 기존 EC2·Docker Hub Secrets는 기존 workflow와 동일하게 필요합니다.

이번 정리는 현재 파일과 향후 커밋을 대상으로 합니다. 이전 커밋에 들어간 값을
Git 이력에서 삭제하는 작업은 수행하지 않습니다. 서버 비밀값이 과거에 공개된 사실이
확인되면 해당 값을 폐기·재발급한 뒤 별도로 이력 정리를 검토합니다.
