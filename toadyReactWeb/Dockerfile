# ==========================================
# 1단계: 빌드 환경 (Node.js)
# ==========================================
# CI(actions/setup-node 22)와 같은 Node 메이저 버전으로 빌드한다.
FROM node:22-alpine AS builder

WORKDIR /app

# Public browser configuration; do not pass server credentials here.
ARG VITE_KAKAO_MAP_KEY
ENV VITE_KAKAO_MAP_KEY=$VITE_KAKAO_MAP_KEY

# 의존성 캐시 활용을 위해 패키지 정의 파일 먼저 복사
COPY package.json package-lock.json ./

# 패키지 명세서와 잠금 파일을 모두 복사하도록 와일드카드(*) 사용
COPY package*.json ./ 

# 의존성 설치
RUN npm ci

# 소스 코드 전체 복사 및 Vite 빌드 실행
COPY . .
RUN npm run build

# ==========================================
# 2단계: 운영 서빙 환경 (Nginx 경량 이미지)
# ==========================================
# CI의 nginx -t 검사와 앞단 nginx 컨테이너와 같은 버전으로 고정한다.
FROM nginx:1.30-alpine

# Nginx 설정 (SPA 라우팅 및 캐싱/gzip 설정 포함). 템플릿으로 두면 nginx 이미지가 켜질 때 환경 변수를 채워
# /etc/nginx/conf.d/default.conf를 만든다. 무중단 배포에서 같은 색 API를 가리키도록 TOADY_API_UPSTREAM만 바꾼다.
ENV TOADY_API_UPSTREAM=toadyWebApi:8080 \
    NGINX_ENVSUBST_FILTER=^TOADY_
COPY nginx.conf /etc/nginx/templates/default.conf.template

# 1단계에서 빌드된 정적 산출물(dist/)만 Nginx 기본 서빙 경로로 복사
COPY --from=builder /app/dist /usr/share/nginx/html

# 컨테이너 서비스 포트
EXPOSE 80

# Nginx 포그라운드 실행
CMD ["nginx", "-g", "daemon off;"]

