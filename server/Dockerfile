# Multi-stage build: compile with Maven, run on a small JRE image.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy the POM first so the dependency download layer caches across code changes.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline || true

COPY src ./src
RUN mvn -q -B clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/stock-aggregation-server.jar app.jar

EXPOSE 8080

# The api service runs this as-is; the loader overrides with
# --spring.profiles.active=loader in docker-compose.
ENTRYPOINT ["java", "-jar", "app.jar"]
