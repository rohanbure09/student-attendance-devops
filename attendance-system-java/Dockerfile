# ---- Stage 1: compile with the JDK ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY src ./src
RUN mkdir -p out/classes \
    && javac --release 17 -d out/classes src/main/java/attendance/*.java \
    && jar --create --file /attendance.jar --main-class attendance.Main -C out/classes .

# ---- Stage 2: small runtime image with only the JRE ----
FROM eclipse-temurin:21-jre
ENV PORT=8080 \
    DATA_DIR=/data

RUN useradd --create-home appuser \
    && mkdir -p /data \
    && chown appuser:appuser /data

WORKDIR /app
COPY --from=build /attendance.jar app.jar

USER appuser
VOLUME ["/data"]
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=8s --start-period=15s --retries=3 \
  CMD ["java", "-cp", "app.jar", "attendance.HealthCheck"]

CMD ["java", "-jar", "app.jar"]
