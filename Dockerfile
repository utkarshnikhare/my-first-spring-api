# ---------- Build stage: compile with Maven on JDK 21 ----------
FROM maven:3.9.6-eclipse-temurin-21 AS build
ARG BUILD_TIMESTAMP=2026-09-06T03:00:00Z
LABEL build.timestamp=${BUILD_TIMESTAMP}
WORKDIR /build
ENV MAVEN_OPTS="-Xmx512m -XX:MaxMetaspaceSize=128m"

# Cache dependencies: copy pom first, download, then add sources
COPY my-first-spring-api/pom.xml .
RUN mvn -B -q dependency:go-offline

COPY my-first-spring-api/src ./src
RUN mvn -B -q -DskipTests package

# ---------- Runtime stage: slim JRE 21 ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar

# Render provides PORT; tune JVM for small instances
ENV JAVA_OPTS="-Xmx256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Listens on the PORT provided by the hosting platform;
# falls back to 8081 when run locally via:  docker run -p 8081:8081 sociomart
EXPOSE 8081
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
