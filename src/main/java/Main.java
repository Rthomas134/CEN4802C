import java.io.IOException;

/**
 * Main.java
 *
 * Entry point for the Task Tracker web application. Starts an HTTP
 * server on port 8080 backed by an in-memory TaskStore. Once running,
 * open a web browser to http://localhost:8080/ to use the app.
 */
public class Main {

    private static final int PORT = 8080;

    public static void main(String[] args) throws IOException {
        TaskStore store = new TaskStore();
        TaskTrackerServer server = new TaskTrackerServer(store);
        server.start(PORT);

        System.out.println("Task Tracker is running.");
        System.out.println("Open your browser to: http://localhost:" + PORT + "/");
        System.out.println("Press Ctrl+C to stop the server.");
    }
}
