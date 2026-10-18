FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew ./gradlew
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
COPY api ./api
COPY domain ./domain
COPY server ./server

RUN chmod +x gradlew \
    && ./gradlew --no-daemon :server:bootJar \
    && cp server/build/libs/*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app
COPY --from=build --chown=app:app /workspace/app.jar ./app.jar

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
