import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TaskStore.java
 *
 * Holds the application's tasks in memory and contains all of the
 * "business logic" for the app: assigning ids, validating input,
 * marking tasks complete, and answering questions like how many tasks
 * are still active. This is the piece that makes the app more than a
 * static page -- every request that touches it can change what every
 * other request sees.
 *
 * A real deployment would replace this with a database, but keeping
 * everything in memory keeps the project small and dependency-free,
 * which is the point for this assignment.
 */
public class TaskStore {

    private final List<Task> tasks = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger nextId = new AtomicInteger(1);

    /**
     * Adds a new task. Rejects blank descriptions rather than silently
     * creating an empty task -- this is the "meaningful logic" of the
     * add operation, not just storing whatever came in on the form.
     *
     * @param rawDescription the description typed into the form
     * @return true if a task was added, false if the input was invalid
     */
    public boolean addTask(String rawDescription) {
        if (rawDescription == null) {
            return false;
        }
        String description = rawDescription.trim();
        if (description.isEmpty()) {
            return false;
        }
        tasks.add(new Task(nextId.getAndIncrement(), description));
        return true;
    }

    /** Marks the task with the given id as done, if it exists. */
    public boolean completeTask(int id) {
        for (Task t : tasks) {
            if (t.getId() == id) {
                t.markDone();
                return true;
            }
        }
        return false;
    }

    /** Returns all tasks in the order they were created. */
    public List<Task> getAllTasks() {
        return new ArrayList<>(tasks);
    }

    public long countActive() {
        return tasks.stream().filter(t -> !t.isDone()).count();
    }

    public long countCompleted() {
        return tasks.stream().filter(Task::isDone).count();
    }
}
