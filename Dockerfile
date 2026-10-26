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
ENTRYPOINT ["java", "-jar", "app.jar"]