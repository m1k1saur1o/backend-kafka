# syntax=docker/dockerfile:1
# Construccion: docker build -t auth-service:latest ./auth-service

# ---------- Etapa 1: compilacion con Maven ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
# Las pruebas se ejecutan con "mvn clean verify" fuera de la imagen (ver README)
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests

# ---------- Etapa 2: runtime minimo con JRE ----------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /build/target/auth-service.jar app.jar
USER app

ENV SERVER_PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
  CMD wget -qO- "http://127.0.0.1:${SERVER_PORT}/actuator/health" | grep -q '"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
