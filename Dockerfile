# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests clean package

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && apt-get clean \
    && groupadd --system --gid 1001 app \
    && useradd --system --uid 1001 --gid app --home-dir /app app \
    && mkdir -p /app/data/uploads \
    && chown -R app:app /app

COPY --from=build /workspace/target/*.jar /app/app.jar
RUN chown app:app /app/app.jar

ENV STORAGE_LOCATION=/app/data/uploads
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom"
ENV PORT=8080

EXPOSE 8080

USER app

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS "http://127.0.0.1:${PORT}/actuator/health" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
