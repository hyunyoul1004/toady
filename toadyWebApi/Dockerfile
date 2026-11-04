FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./

RUN chmod +x gradlew \
	&& ./gradlew --no-daemon dependencies

COPY src ./src

RUN ./gradlew --no-daemon bootJar -x test \
	&& cp build/libs/*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN groupadd --system spring \
	&& useradd --system --gid spring --create-home spring \
	&& mkdir -p /app/logs \
	&& chown -R spring:spring /app

COPY --from=build --chown=spring:spring /workspace/app.jar /app/app.jar

ENV LOG_PATH=/app/logs

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
