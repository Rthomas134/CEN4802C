import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TaskStoreLoggingTest.java
 *
 * Verifies that TaskStore records the right log entries, at the right
 * levels, for the events it handles. A small in-memory Handler is attached
 * to the "tasktracker.store" logger so each test can inspect exactly which
 * LogRecords were produced -- the log output is treated as part of the
 * class's observable behavior, just like its return values.
 */
class TaskStoreLoggingTest {

    private static final Logger STORE_LOG = Logger.getLogger("tasktracker.store");

    private final List<LogRecord> records = new ArrayList<>();
    private final Handler capture = new Handler() {
        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    private Level originalLevel;
    private boolean originalUseParent;
    private TaskStore store;

    @BeforeEach
    void setUp() {
        originalLevel = STORE_LOG.getLevel();
        originalUseParent = STORE_LOG.getUseParentHandlers();
        STORE_LOG.setLevel(Level.ALL);          // capture FINE entries too
        STORE_LOG.setUseParentHandlers(false);  // keep test output quiet
        STORE_LOG.addHandler(capture);
        store = new TaskStore();
    }

    @AfterEach
    void tearDown() {
        STORE_LOG.removeHandler(capture);
        STORE_LOG.setLevel(originalLevel);
        STORE_LOG.setUseParentHandlers(originalUseParent);
    }

    private LogRecord onlyRecord() {
        assertEquals(1, records.size(), "expected exactly one log entry, got: " + messages());
        return records.get(0);
    }

    private List<String> messages() {
        List<String> m = new ArrayList<>();
        for (LogRecord r : records) {
            m.add(r.getLevel() + " " + r.getMessage());
        }
        return m;
    }

    @Test
    void addTask_success_logsInfoWithIdPriorityAndDescription() {
        store.addTask("Buy groceries", "HIGH");

        LogRecord r = onlyRecord();
        assertEquals(Level.INFO, r.getLevel());
        assertTrue(r.getMessage().contains("id=1"));
        assertTrue(r.getMessage().contains("priority=HIGH"));
        assertTrue(r.getMessage().contains("Buy groceries"));
    }

    @Test
    void addTask_blankDescription_logsWarning() {
        store.addTask("   ", "HIGH");

        LogRecord r = onlyRecord();
        assertEquals(Level.WARNING, r.getLevel());
        assertTrue(r.getMessage().contains("blank description"));
    }

    @Test
    void addTask_unrecognizedPriority_logsWarningThenInfo() {
        store.addTask("Some task", "urgent");

        assertEquals(2, records.size(), messages().toString());
        assertEquals(Level.WARNING, records.get(0).getLevel());
        assertTrue(records.get(0).getMessage().contains("'urgent'"));
        assertEquals(Level.INFO, records.get(1).getLevel());
    }

    @Test
    void addTask_overlongDescription_isTruncatedAndLogged() {
        String longText = "x".repeat(TaskStore.MAX_DESCRIPTION_LENGTH + 50);

        store.addTask(longText, "LOW");

        assertEquals(TaskStore.MAX_DESCRIPTION_LENGTH, store.getAllTasks().get(0).getDescription().length());
        assertEquals(Level.WARNING, records.get(0).getLevel());
        assertTrue(records.get(0).getMessage().contains("truncated"));
    }

    @Test
    void completeTask_unknownId_logsWarningWithTheId() {
        store.completeTask(42);

        LogRecord r = onlyRecord();
        assertEquals(Level.WARNING, r.getLevel());
        assertTrue(r.getMessage().contains("id=42"));
    }

    @Test
    void completeTask_alreadyDone_logsOnlyAtFine() {
        store.addTask("Task", "LOW");
        store.completeTask(1);
        records.clear();

        store.completeTask(1);

        LogRecord r = onlyRecord();
        assertEquals(Level.FINE, r.getLevel(), "a repeat completion is debug detail, not a problem");
    }

    @Test
    void deleteTask_logsInfoOnSuccessAndWarningOnUnknownId() {
        store.addTask("Task", "LOW");
        records.clear();

        store.deleteTask(1);
        store.deleteTask(1);

        assertEquals(Level.INFO, records.get(0).getLevel());
        assertEquals(Level.WARNING, records.get(1).getLevel());
    }

    @Test
    void raisingTheLevelToWarning_suppressesInfoEntries() {
        STORE_LOG.setLevel(Level.WARNING);

        store.addTask("Routine task", "LOW");   // INFO -> filtered out
        store.deleteTask(999);                  // WARNING -> recorded

        LogRecord r = onlyRecord();
        assertEquals(Level.WARNING, r.getLevel());
    }
}
