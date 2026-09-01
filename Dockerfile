# ---------- Build stage: compile with Maven on JDK 21 ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Cache dependencies: copy pom first, download, then add sources
COPY my-first-spring-api/pom.xml .
RUN mvn -B -q dependency:go-offline

COPY my-first-spring-api/src ./src
RUN mvn -B -q -DskipTests package

# ---------- Runtime stage: slim JRE 21 ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar

# Listens on the PORT provided by the platform (Render injects it);
# falls back to 8081 when run locally via:  docker run -p 8081:8081 sociomart
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
