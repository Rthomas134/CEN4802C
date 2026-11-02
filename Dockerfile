FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the already-built, already-tested jar from the host's target/
# directory into the image.
COPY target/task-tracker-1.0.jar app.jar

# Logging: java.util.logging writes to the console (captured by
# `docker logs`) and to /app/logs/task-tracker.0.log inside the container.
# Change how much is recorded at run time without rebuilding, e.g.:
#   docker run -e LOG_LEVEL=FINE ...
ENV LOG_LEVEL=INFO

# The Task Tracker web server listens on 8080.
EXPOSE 8080

# Starts the packaged application the same way you'd run it locally
# with `java -jar target/task-tracker-1.0.jar`.
# Monitoring: Docker calls GET /health every 15 s and marks the container
# "healthy" or "unhealthy" (visible in `docker ps` and `docker inspect`).
# Uses bash's built-in /dev/tcp so no extra tools are needed in the image.
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /health HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3 && grep -q "\"status\":\"UP\"" <&3'

ENTRYPOINT ["java", "-jar", "app.jar"]
