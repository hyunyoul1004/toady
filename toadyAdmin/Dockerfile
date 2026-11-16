FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace
# 의존성만 먼저 받아 별도 레이어로 둔다. 소스만 바뀐 배포는 CI 빌드 캐시가 이 레이어를 재사용한다.
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew \
	&& ./gradlew --no-daemon dependencies > /dev/null

COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test \
	&& boot_jar="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -print -quit)" \
	&& test -n "$boot_jar" \
	&& cp "$boot_jar" /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app
RUN groupadd --system spring \
	&& useradd --system --gid spring --create-home spring
COPY --from=build --chown=spring:spring /workspace/app.jar /app/app.jar

USER spring:spring
EXPOSE 8083
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
