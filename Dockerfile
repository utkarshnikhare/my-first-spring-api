# ---------- Build stage: compile with Maven on JDK 21 ----------
FROM maven:3.9.6-eclipse-temurin-21 AS build
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

# Business time zone (the application also pins it at startup).
ENV TZ=Asia/Kolkata

# Unprivileged user
RUN groupadd --system sociomart && useradd --system --gid sociomart --no-create-home sociomart
COPY --from=build --chown=sociomart:sociomart /build/target/*.jar app.jar
USER sociomart

# Render provides PORT; tune JVM for small instances
ENV JAVA_OPTS="-Xmx256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Listens on the PORT provided by the hosting platform;
# falls back to 8081 when run locally via:  docker run -p 8081:8081 sociomart
EXPOSE 8081

# exec replaces the shell so the JVM is PID 1 and receives SIGTERM (graceful shutdown).
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
