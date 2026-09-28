FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the already-built, already-tested jar from the host's target/
# directory into the image.
COPY target/task-tracker-1.0.jar app.jar

# The Task Tracker web server listens on 8080.
EXPOSE 8080

# Starts the packaged application the same way you'd run it locally
# with `java -jar target/task-tracker-1.0.jar`.
ENTRYPOINT ["java", "-jar", "app.jar"]