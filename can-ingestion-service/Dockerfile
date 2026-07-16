# ---- Build stage: compile the module (and its common-lib dependency) --------
# Build context is the repo root (see docker-compose.yml) so the whole Maven
# reactor is available; -pl/-am builds only this service plus common-lib.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -pl can-ingestion-service -am -DskipTests clean package

# ---- Runtime stage: slim JRE ------------------------------------------------
FROM eclipse-temurin:17-jre-jammy AS runtime
# curl is used by the compose healthcheck to hit the actuator endpoint
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/can-ingestion-service/target/can-ingestion-service-*.jar app.jar
EXPOSE 8081
HEALTHCHECK --interval=15s --timeout=5s --retries=10 --start-period=40s \
  CMD curl -fsS http://localhost:8081/actuator/health | grep -q UP || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
