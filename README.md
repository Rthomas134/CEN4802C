# cen4802 &mdash; Task Tracker

**Author:** Raquel Thomas

## What this is

A small Java web application built for CEN4802 to practice a version-control
workflow (branching, pull requests, diffs, merges) throughout the semester.

Task Tracker is a simple to-do list you use from your web browser. You can:

- add a task by typing a description and clicking **Add**
- mark a task as complete, which crosses it out and moves it out of the
  active count
- see a running count of how many tasks are active vs. completed

The server rejects blank/whitespace-only descriptions rather than silently
adding an empty task, so there's real logic happening on every request, not
just a static page being served.

## How it's built

The app is plain Java with **no external dependencies or frameworks** &mdash;
it uses `com.sun.net.httpserver.HttpServer`, which ships with the JDK. That
keeps the project simple, while still leaving room to add a build tool,
tests, containerization, etc.

Source lives under the standard Maven layout, `src/main/java/`:

- `Task.java` &mdash; a single to-do item (id, description, priority, done
  flag)
- `TaskStore.java` &mdash; in-memory storage plus the app's logic: assigning
  ids, validating input, parsing/sorting by priority, marking tasks done,
  and counting active/completed tasks
- `TaskTrackerServer.java` &mdash; the HTTP server: routes requests, parses
  submitted form data, and renders the HTML page
- `Main.java` &mdash; entry point; starts the server on port 8080

As of this assignment, the project builds with **Maven** (`pom.xml`), which
turns "compile every file, then hand-craft a runnable jar" into one
repeatable command.

## Building it

Requires a JDK (17+) and Maven on your PATH.

```
mvn clean package
```

This compiles the sources, runs packaging, and produces a runnable jar at:

```
target/task-tracker-1.0.jar
```

`pom.xml` configures `maven-jar-plugin` to stamp a `Main-Class: Main` entry
into the jar's manifest, so the jar can be launched directly &mdash; no
IDE and no need to pass the class name on the command line.

## Running it

From the command line, launch the packaged artifact (not the IDE):

```
java -jar target/task-tracker-1.0.jar
```

Then open a browser to **http://localhost:8080/**.

The app stores tasks in memory only, so the list resets every time the
server restarts.

### Rebuilding after a source change

Maven doesn't track partial edits automatically re-run for you, but a
rebuild is still a single command. After changing any file under
`src/main/java/`, just re-run:

```
mvn clean package
```

`clean` removes the old `target/` directory (including the previous jar)
first, so `java -jar target/task-tracker-1.0.jar` afterward is guaranteed
to be running the newly compiled code, not a stale artifact left over from
an earlier build.

## Automated tests

Unit tests live under `src/test/java/`, using **JUnit 5 (Jupiter)**:

- `TaskPriorityTest.java` &mdash; tests `Task.Priority.fromString()`, which
  parses the raw priority string submitted from the web form. Covers
  recognized values (case-insensitively), a null value, and unrecognized
  values, all of which are expected to fall back to `MEDIUM` rather than
  throwing. Also covers `label()` and `cssClass()`.
- `TaskStoreTest.java` &mdash; tests `TaskStore`'s actual business logic:
  rejecting blank/null descriptions on add, trimming whitespace, defaulting
  an unrecognized/missing priority to `MEDIUM`, completing a task by id
  (including the "id doesn't exist" case), the active/completed counts, and
  that `getAllTasksByPriority()` actually sorts High &rarr; Medium &rarr;
  Low rather than just returning insertion order.
- `TaskStoreLoggingTest.java` / `AppLoggingTest.java` &mdash; logging
  behaviour (see "Application logging" below).

Run just the tests:

```
mvn test
```

Test reports are written to `target/surefire-reports/` (not committed --
it's build output, like the rest of `target/`).

Because Maven's build lifecycle runs `test` before `package`, running
`mvn clean package` (see "Building it" above) automatically runs the full
test suite first and **fails the build** if any test fails, without a jar
ever being produced from code that didn't pass its tests.


## Running with Docker

Build the application first (produces `target/task-tracker-1.0.jar`):

    mvn clean package

Build the Docker image:

    docker build -t task-tracker .

Run the container:

    docker run -d -p 8080:8080 --name task-tracker task-tracker

Then open http://localhost:8080 in your browser.

## Application logging

The app logs with **java.util.logging (JUL)**, the logging framework built
into the JDK, so the project still has no runtime dependencies.

| File | Role |
|---|---|
| `src/main/resources/logging.properties` | Handlers, levels, output format, log file location |
| `src/main/java/AppLogging.java` | Loads the config at startup; applies the `LOG_LEVEL` override |
| `TaskTrackerServer`, `TaskStore`, `Main` | Log the actual application events |

### Where the logs go

- **Console (stdout/stderr):** what `docker logs` and `kubectl logs` read.
- **File:** `logs/task-tracker.0.log` (relative to where the app is started).
  Rotates at 5 MB and keeps 5 files (`.0` is the newest). Appends across
  restarts. The `logs/` folder is git-ignored.

Each entry is one line: timestamp, level, logger, message. Exceptions add
their stack trace on the lines below.

```
2026-10-19 20:09:13.474 INFO    [tasktracker.store] Task added: id=1 priority=HIGH description="Buy milk" (1 active)
2026-10-19 20:09:13.475 INFO    [tasktracker.http] req-2 POST /add -> 303 in 7 ms from 127.0.0.1
2026-10-19 20:09:13.517 WARNING [tasktracker.store] Delete failed: no task with id=99 (2 tasks in store)
2026-10-19 20:09:13.532 SEVERE  [tasktracker.http] req-8 POST /add from 127.0.0.1 failed with IllegalArgumentException: ...
```

### Levels used

| Level | Used for |
|---|---|
| `SEVERE` | A request failed with an unexpected exception (HTTP 500), or the server could not start |
| `WARNING` | Bad input or a request that could not be honored: blank description, unknown/malformed id, unrecognized priority, 404/405 |
| `INFO` | Normal events: every request (method, path, status, time, client), task added/completed/deleted, startup/shutdown |
| `CONFIG` | Startup configuration details |
| `FINE` | Debug detail: submitted form fields, user agent, page render size, repeat completions |

### Changing how much is logged

No rebuild needed: set `LOG_LEVEL` (or `-Dlog.level=`). `DEBUG`, `WARN` and
`ERROR` are accepted as aliases for `FINE`, `WARNING` and `SEVERE`.

```bash
LOG_LEVEL=FINE    java -jar target/task-tracker-1.0.jar   # Git Bash / Linux / macOS
$env:LOG_LEVEL="WARNING"; java -jar target/task-tracker-1.0.jar   # PowerShell
java -Dlog.level=FINE -jar target/task-tracker-1.0.jar   # any shell
docker run -d -p 8080:8080 -e LOG_LEVEL=FINE --name task-tracker task-tracker
```

In Kubernetes, change `LOG_LEVEL` in `deployment.yaml` and `kubectl apply`.
To replace the whole configuration, pass
`-Djava.util.logging.config.file=path/to/other.properties`.

### Generating traffic

With the app running, `scripts/generate-traffic.sh [sessions]` sends
realistic requests (adds, completes, deletes, page loads, plus occasional bad
input and one malformed request) through the HTTP API. The script writes no
log lines itself; every entry comes from the app handling the requests.

```bash
./scripts/generate-traffic.sh 30
BASE_URL=http://localhost:30080 ./scripts/generate-traffic.sh 30   # Kubernetes NodePort
```

### Examining and filtering

```bash
wc -l logs/task-tracker.0.log                       # how many entries
grep -E " (WARNING|SEVERE) " logs/task-tracker.0.log  # only problems
grep -c "Task added" logs/task-tracker.0.log        # count an event type
grep -A 3 "SEVERE" logs/task-tracker.0.log          # errors with stack trace
grep "req-57 " logs/task-tracker.0.log              # follow one request
grep "id=7 " logs/task-tracker.0.log                 # history of one task
# Most common problems, with numbers masked so similar lines group together:
grep -E " (WARNING|SEVERE) " logs/task-tracker.0.log | cut -c25- | sed -E 's/[0-9]+/#/g' | sort | uniq -c | sort -rn
docker logs task-tracker 2>&1 | grep WARNING
kubectl logs -l app=task-tracker --prefix | grep SEVERE
```

Unit tests for logging behaviour: `TaskStoreLoggingTest` (captures the log
records `TaskStore` produces and checks their levels and content) and
`AppLoggingTest` (the `LOG_LEVEL` parsing).
