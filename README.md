# cen4802C — Task Tracker

**Author:** Raquel Thomas

## What this is

A small Java web application built for CEN4802 to practice a version-control
workflow (branching, pull requests, diffs, merges) throughout the semester.

Task Tracker is a simple to-do list you use from your web browser. You can:

* add a task by typing a description and clicking **Add**
* mark a task as complete, which crosses it out and moves it out of the
active count
* see a running count of how many tasks are active vs. completed

The server rejects blank/whitespace-only descriptions rather than silently
adding an empty task, so there's real logic happening on every request, not
just a static page being served.

## How it's built

The app is plain Java with **no external dependencies or frameworks** —
it uses `com.sun.net.httpserver.HttpServer`, which ships with the JDK. That
keeps the project simple to build and run right now, while still leaving
room to add a build tool, tests, containerization, etc. in later assignments.

* `Task.java` — a single to-do item (id, description, done flag)
* `TaskStore.java` — in-memory storage plus the app's logic: assigning
ids, validating input, marking tasks done, and counting active/completed
tasks
* `TaskTrackerServer.java` — the HTTP server: routes requests, parses
submitted form data, and renders the HTML page
* `Main.java` — entry point; starts the server on port 8080

## Running it

Requires a JDK (11+) on your PATH.

```
cd src
javac -d ../out \\\\\\\*.java
cd ..
java -cp out Main
```

Then open a browser to **http://localhost:8080/**.

The app stores tasks in memory only, so the list resets every time the
server restarts.

