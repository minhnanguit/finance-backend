# Image production của backend. Build ngay trên server (ARM64 hoặc x86 đều được, image base đa kiến trúc):
#   docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env.prod up -d --build
# Test KHÔNG chạy ở đây — CI đã chạy trước khi merge.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
# Wrapper + script build trước, để layer tải dependency được cache khi chỉ sửa code.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon dependencies > /dev/null
COPY api api
COPY src/main src/main
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar \
    && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} /app.jar \;

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /app.jar app.jar
USER app
EXPOSE 18473
# Heap theo giới hạn RAM của container (mem_limit trong compose), không theo RAM cả máy.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "app.jar"]
