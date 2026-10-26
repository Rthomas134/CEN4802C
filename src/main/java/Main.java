import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main.java
 *
 * Entry point for the Task Tracker web application. Starts an HTTP
 * server on port 8080 backed by an in-memory TaskStore. Once running,
 * open a web browser to http://localhost:8080/ to use the app.
 */
public class Main {

    private static final int PORT = 8080;

    public static void main(String[] args) {
        AppLogging.init();
        Logger log = Logger.getLogger(AppLogging.ROOT + ".main");

        log.config("Java " + System.getProperty("java.version")
                + ", working directory " + System.getProperty("user.dir"));

        TaskStore store = new TaskStore();
        TaskTrackerServer server = new TaskTrackerServer(store);
        try {
            server.start(PORT);
        } catch (IOException e) {
            // Most commonly: port 8080 is already in use by another process.
            log.log(Level.SEVERE, "Could not start HTTP server on port " + PORT
                    + " (is something else already using it?)", e);
            System.exit(1);
        }

        log.info("Task Tracker started on http://localhost:" + PORT
                + "/ (log level " + AppLogging.effectiveLevel() + ")");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Task Tracker shutting down: " + store.countActive() + " active, "
                    + store.countCompleted() + " completed tasks discarded (in-memory store)");
            AppLogging.shutdown();
        }));

        System.out.println("Open your browser to: http://localhost:" + PORT + "/");
        System.out.println("Press Ctrl+C to stop the server.");
    }
}
