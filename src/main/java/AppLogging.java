import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * AppLogging.java
 *
 * Sets up java.util.logging (the logging framework built into the JDK) for
 * the Task Tracker. Called once, first thing in Main, before anything else
 * logs.
 *
 * Order of precedence for the configuration:
 *   1. -Djava.util.logging.config.file=...  (a whole replacement file)
 *   2. logging.properties bundled in the jar (src/main/resources)
 *   3. LOG_LEVEL env var / -Dlog.level=... overrides the level from 1 or 2,
 *      so the amount of detail can be changed at run time without
 *      rebuilding -- e.g. `docker run -e LOG_LEVEL=FINE ...`.
 */
public final class AppLogging {

    /** Parent logger for everything the application itself logs. */
    public static final String ROOT = "tasktracker";

    private AppLogging() {
    }

    public static void init() {
        // Use a LogManager that keeps logging alive during JVM shutdown, so
        // the "shutting down" entry from Main's shutdown hook is not lost.
        // Must be set before the first Logger is created.
        if (System.getProperty("java.util.logging.manager") == null) {
            System.setProperty("java.util.logging.manager", ShutdownSafeLogManager.class.getName());
        }
        try {
            // FileHandler fails if its directory does not exist yet.
            Files.createDirectories(Path.of("logs"));
        } catch (IOException e) {
            System.err.println("Could not create logs/ directory: " + e);
        }

        if (System.getProperty("java.util.logging.config.file") == null) {
            try (InputStream in = AppLogging.class.getResourceAsStream("/logging.properties")) {
                if (in != null) {
                    LogManager.getLogManager().readConfiguration(in);
                } else {
                    System.err.println("logging.properties not found on classpath; using JDK defaults");
                }
            } catch (IOException e) {
                System.err.println("Could not load logging.properties: " + e);
            }
        }

        String override = System.getProperty("log.level", System.getenv("LOG_LEVEL"));
        if (override != null && !override.isBlank()) {
            applyLevel(override.trim());
        }

        Logger log = Logger.getLogger(ROOT);
        log.config("Logging initialised: level=" + effectiveLevel()
                + ", file=logs/task-tracker.0.log, override="
                + (override == null || override.isBlank() ? "none" : override));
    }

    /**
     * Sets the minimum level for the application's loggers. Accepts the
     * JDK names (SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST, ALL,
     * OFF) plus the common aliases DEBUG -> FINE, WARN -> WARNING and
     * ERROR -> SEVERE.
     */
    static void applyLevel(String name) {
        Level level;
        try {
            level = Level.parse(alias(name.toUpperCase()));
        } catch (IllegalArgumentException e) {
            Logger.getLogger(ROOT).warning("Unknown LOG_LEVEL '" + name + "', keeping configured level");
            return;
        }
        Logger.getLogger(ROOT).setLevel(level);
        // Make sure the handlers will actually pass the finer records through.
        for (Handler h : Logger.getLogger("").getHandlers()) {
            if (h.getLevel().intValue() > level.intValue()) {
                h.setLevel(level);
            }
        }
    }

    private static String alias(String name) {
        switch (name) {
            case "DEBUG": return "FINE";
            case "TRACE": return "FINER";
            case "WARN":  return "WARNING";
            case "ERROR": return "SEVERE";
            default:      return name;
        }
    }

    static Level effectiveLevel() {
        Logger l = Logger.getLogger(ROOT);
        while (l != null && l.getLevel() == null) {
            l = l.getParent();
        }
        return l == null ? Level.INFO : l.getLevel();
    }

    /**
     * The JDK's default LogManager closes every handler from its own
     * shutdown hook, which races with application shutdown hooks and
     * usually swallows their final log entries. This subclass ignores that
     * automatic reset; handlers are flushed and closed explicitly by
     * {@link #shutdown()} at the very end of Main's shutdown hook instead.
     */
    public static class ShutdownSafeLogManager extends LogManager {
        @Override
        public void reset() {
            // Skip only the automatic reset run by the JDK's own shutdown
            // hook thread (LogManager$Cleaner); normal resets, such as the
            // one readConfiguration() does, still happen.
            if (Thread.currentThread().getClass().getName().endsWith("LogManager$Cleaner")) {
                return;
            }
            super.reset();
        }

        void reallyReset() {
            super.reset();
        }
    }

    /** Flushes and closes all handlers. Call once, last thing on shutdown. */
    public static void shutdown() {
        LogManager manager = LogManager.getLogManager();
        if (manager instanceof ShutdownSafeLogManager) {
            ((ShutdownSafeLogManager) manager).reallyReset();
        } else {
            manager.reset();
        }
    }
}
