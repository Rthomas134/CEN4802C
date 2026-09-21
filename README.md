# cen4802 &mdash; Task Tracker

**Author:** Rawkk

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
