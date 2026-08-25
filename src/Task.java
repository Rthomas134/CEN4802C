/**
 * Task.java
 *
 * A single to-do item. Plain data holder used by TaskStore; the server
 * reads these fields to render HTML and never exposes them as raw JSON
 * or anything like that -- this is intentionally a small, simple model.
 */
public class Task {

    private final int id;
    private final String description;
    private boolean done;

    public Task(int id, String description) {
        this.id = id;
        this.description = description;
        this.done = false;
    }

    public int getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public boolean isDone() {
        return done;
    }

    public void markDone() {
        this.done = true;
    }
}
