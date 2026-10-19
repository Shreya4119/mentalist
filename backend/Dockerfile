# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

COPY --chmod=755 mvnw mvnw
COPY .mvn/ .mvn/
COPY pom.xml pom.xml
RUN ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B package -DskipTests

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# Fonts so PDF pages can be rendered to images (large-report preprocessing).
RUN apt-get update && apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/*.jar app.jar
# /data/uploads holds report files (STORAGE_DIR); created here so the
# named volume inherits appuser ownership.
RUN useradd --system --create-home --shell /usr/sbin/nologin appuser \
    && mkdir -p /data/uploads \
    && chown -R appuser:appuser /app /data
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-Djava.awt.headless=true", "-jar", "app.jar"]
