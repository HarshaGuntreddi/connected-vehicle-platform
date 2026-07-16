# ---- Build stage ------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -pl dbc-decoder-service -am -DskipTests clean package

# ---- Runtime stage ----------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/dbc-decoder-service/target/dbc-decoder-service-*.jar app.jar
EXPOSE 8082
HEALTHCHECK --interval=15s --timeout=5s --retries=10 --start-period=40s \
  CMD curl -fsS http://localhost:8082/actuator/health | grep -q UP || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
