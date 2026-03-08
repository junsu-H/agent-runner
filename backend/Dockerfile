# ── Stage 1: Build ──
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

COPY build.gradle.kts settings.gradle.kts gradlew ./
COPY gradle/ gradle/
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# ── Stage 2: Run ──
FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 19876
ENTRYPOINT ["java", "-jar", "app.jar"]
