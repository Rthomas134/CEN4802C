import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

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

    private static final Logger LOG = Logger.getLogger(AppLogging.ROOT + ".store");

    /** Longest description accepted; longer input is truncated (and logged). */
    static final int MAX_DESCRIPTION_LENGTH = 200;

    private final List<Task> tasks = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger nextId = new AtomicInteger(1);

    /**
     * Adds a new task. Rejects blank descriptions rather than silently
     * creating an empty task -- this is the "meaningful logic" of the
     * add operation, not just storing whatever came in on the form.
     *
     * @param rawDescription the description typed into the form
     * @param rawPriority    the priority selected on the form (e.g. "High");
     *                       defaults to MEDIUM if missing or unrecognized
     * @return true if a task was added, false if the input was invalid
     */
    public boolean addTask(String rawDescription, String rawPriority) {
        if (rawDescription == null) {
            LOG.warning("Add rejected: description field was missing from the request");
            return false;
        }
        String description = rawDescription.trim();
        if (description.isEmpty()) {
            LOG.warning("Add rejected: blank description (raw length=" + rawDescription.length() + ")");
            return false;
        }
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            LOG.warning("Description truncated from " + description.length() + " to "
                    + MAX_DESCRIPTION_LENGTH + " characters");
            description = description.substring(0, MAX_DESCRIPTION_LENGTH);
        }
        Task.Priority priority = Task.Priority.fromString(rawPriority);
        if (!priority.name().equalsIgnoreCase(rawPriority == null ? "" : rawPriority.trim())) {
            LOG.warning("Unrecognized priority '" + rawPriority + "', defaulted to " + priority);
        }
        Task task = new Task(nextId.getAndIncrement(), description, priority);
        tasks.add(task);
        LOG.info("Task added: id=" + task.getId() + " priority=" + priority
                + " description=\"" + description + "\" (" + countActive() + " active)");
        return true;
    }

    /** Marks the task with the given id as done, if it exists. */
    public boolean completeTask(int id) {
        for (Task t : tasks) {
            if (t.getId() == id) {
                if (t.isDone()) {
                    LOG.fine("Complete ignored: task id=" + id + " was already done");
                    return true;
                }
                t.markDone();
                LOG.info("Task completed: id=" + id + " priority=" + t.getPriority()
                        + " description=\"" + t.getDescription() + "\" (" + countActive() + " active, "
                        + countCompleted() + " completed)");
                return true;
            }
        }
        LOG.warning("Complete failed: no task with id=" + id + " (" + tasks.size() + " tasks in store)");
        return false;
    }

    /** Removes the task with the given id, if it exists. */
    public boolean deleteTask(int id) {
        boolean removed = tasks.removeIf(t -> t.getId() == id);
        if (removed) {
            LOG.info("Task deleted: id=" + id + " (" + tasks.size() + " tasks remain)");
        } else {
            LOG.warning("Delete failed: no task with id=" + id + " (" + tasks.size() + " tasks in store)");
        }
        return removed;
    }

    /** Returns all tasks in the order they were created. */
    public List<Task> getAllTasks() {
        return new ArrayList<>(tasks);
    }

    /**
     * Returns all tasks sorted so higher-priority tasks (High, then
     * Medium, then Low) come first; tasks with the same priority keep
     * their original creation order. This is what the task list page
     * actually renders, so higher-priority work is visible at a glance
     * instead of being buried in creation order.
     */
    public List<Task> getAllTasksByPriority() {
        List<Task> sorted = new ArrayList<>(tasks);
        sorted.sort(Comparator.comparing(t -> t.getPriority().ordinal()));
        return sorted;
    }

    public long countActive() {
        return tasks.stream().filter(t -> !t.isDone()).count();
    }

    public long countCompleted() {
        return tasks.stream().filter(Task::isDone).count();
    }
}
