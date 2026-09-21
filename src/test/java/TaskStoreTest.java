import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TaskStoreTest.java
 *
 * Exercises TaskStore, which holds the application's actual business
 * logic: validating input on add, looking up tasks to complete them,
 * and answering the active/completed counts and the priority-sorted
 * view the web page renders. A fresh TaskStore is created before every
 * test (see setUp) so tests can't influence each other by sharing state.
 */
class TaskStoreTest {

    private TaskStore store;

    @BeforeEach
    void setUp() {
        store = new TaskStore();
    }

    // ---- addTask: valid input ------------------------------------------

    @Test
    void addTask_withValidDescription_returnsTrueAndIsStored() {
        boolean added = store.addTask("Buy groceries", "HIGH");

        assertTrue(added, "addTask should return true for a non-blank description");
        assertEquals(1, store.getAllTasks().size());
        assertEquals("Buy groceries", store.getAllTasks().get(0).getDescription());
    }

    @Test
    void addTask_trimsSurroundingWhitespaceFromDescription() {
        store.addTask("   Walk the dog   ", "LOW");

        assertEquals("Walk the dog", store.getAllTasks().get(0).getDescription());
    }

    // ---- addTask: invalid input -----------------------------------------

    @Test
    void addTask_withNullDescription_returnsFalseAndAddsNothing() {
        boolean added = store.addTask(null, "HIGH");

        assertFalse(added);
        assertTrue(store.getAllTasks().isEmpty());
    }

    @Test
    void addTask_withBlankDescription_returnsFalseAndAddsNothing() {
        // Whitespace-only input should be rejected the same as an empty
        // string -- it isn't a real task description.
        boolean added = store.addTask("   ", "HIGH");

        assertFalse(added);
        assertTrue(store.getAllTasks().isEmpty());
    }

    // ---- addTask: priority defaulting -----------------------------------

    @Test
    void addTask_withMissingPriority_defaultsToMedium() {
        store.addTask("Some task", null);

        assertEquals(Task.Priority.MEDIUM, store.getAllTasks().get(0).getPriority());
    }

    @Test
    void addTask_withUnrecognizedPriority_defaultsToMedium() {
        store.addTask("Some task", "urgent!!");

        assertEquals(Task.Priority.MEDIUM, store.getAllTasks().get(0).getPriority());
    }

    // ---- completeTask -----------------------------------------------------

    @Test
    void completeTask_withExistingId_marksTaskDoneAndReturnsTrue() {
        store.addTask("Finish report", "HIGH");
        int id = store.getAllTasks().get(0).getId();

        boolean result = store.completeTask(id);

        assertTrue(result);
        assertTrue(store.getAllTasks().get(0).isDone());
    }

    @Test
    void completeTask_withUnknownId_returnsFalseAndChangesNothing() {
        store.addTask("Finish report", "HIGH");

        boolean result = store.completeTask(9999);

        assertFalse(result, "completing a nonexistent id should fail rather than throw or silently succeed");
        assertFalse(store.getAllTasks().get(0).isDone(), "the existing task must be untouched");
    }
    // ---- deleteTask -----------------------------------------------------

    @Test
    void deleteTask_withExistingId_removesTaskAndReturnsTrue() {
        store.addTask("Finish report", "HIGH");
        int id = store.getAllTasks().get(0).getId();

        boolean removed = store.deleteTask(id);

        assertTrue(removed, "deleting an existing task should return true");
        assertTrue(store.getAllTasks().isEmpty(), "the deleted task should no longer be in the store");
    }

    @Test
    void deleteTask_withUnknownId_returnsFalseAndChangesNothing() {
        store.addTask("Finish report", "HIGH");

        boolean removed = store.deleteTask(9999);

        assertFalse(removed, "deleting a nonexistent id should fail rather than throw or silently succeed");
        assertEquals(1, store.getAllTasks().size(), "the existing task must be untouched");
    }

    @Test
    void deleteTask_doesNotAffectOtherTasks() {
        store.addTask("Task A", "HIGH");
        store.addTask("Task B", "MEDIUM");
        int idToDelete = store.getAllTasks().get(0).getId();

        store.deleteTask(idToDelete);

        assertEquals(1, store.getAllTasks().size());
        assertEquals("Task B", store.getAllTasks().get(0).getDescription());
    }

    // ---- counts -------------------------------------------------------------

    @Test
    void counts_reflectActiveAndCompletedTasksAfterCompletingOne() {
        store.addTask("Task A", "HIGH");
        store.addTask("Task B", "MEDIUM");
        store.addTask("Task C", "LOW");
        int firstId = store.getAllTasks().get(0).getId();

        store.completeTask(firstId);

        assertEquals(2, store.countActive());
        assertEquals(1, store.countCompleted());
    }

    // ---- getAllTasksByPriority ------------------------------------------------

    @Test
    void getAllTasksByPriority_ordersHighBeforeMediumBeforeLow() {
        // Deliberately added out of priority order, so a passing test
        // actually proves the sort is happening rather than just
        // reflecting insertion order by coincidence.
        store.addTask("Low priority item", "LOW");
        store.addTask("High priority item", "HIGH");
        store.addTask("Medium priority item", "MEDIUM");

        List<Task> sorted = store.getAllTasksByPriority();

        assertEquals(Task.Priority.HIGH, sorted.get(0).getPriority());
        assertEquals(Task.Priority.MEDIUM, sorted.get(1).getPriority());
        assertEquals(Task.Priority.LOW, sorted.get(2).getPriority());
    }

    @Test
    void getAllTasksByPriority_keepsCreationOrderWithinSamePriority() {
        store.addTask("First high task", "HIGH");
        store.addTask("Second high task", "HIGH");

        List<Task> sorted = store.getAllTasksByPriority();

        assertEquals("First high task", sorted.get(0).getDescription());
        assertEquals("Second high task", sorted.get(1).getDescription());
    }
}
