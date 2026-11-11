# syntax=docker/dockerfile:1

# 1. Build: Gradle makes one jar with every dependency (Ktor's buildFatJar). Tests run before deploying, not here.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
# Build files first: dependencies stay cached while only the source changes
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN ./gradlew dependencies --no-daemon -q > /dev/null
COPY src ./src
RUN ./gradlew buildFatJar --no-daemon -q -x test

# 2. Run: only the Java 21 runtime and the jar
FROM eclipse-temurin:21-jre
RUN useradd --system --no-create-home martp
WORKDIR /app
COPY --from=build /app/build/libs/martp-api.jar ./martp-api.jar
USER martp
# Cloud Run sets PORT; application.yaml reads it (8080 by default)
EXPOSE 8080
# The heap gets 75% of the container's memory, the rest is for the JVM itself. Out of memory = restart, never a half-broken API.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "martp-api.jar"]
