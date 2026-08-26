/**
 * Task.java
 *
 * A single to-do item. Plain data holder used by TaskStore; the server
 * reads these fields to render HTML and never exposes them as raw JSON
 * or anything like that -- this is intentionally a small, simple model.
 */
public class Task {

    /**
     * How urgently a task needs attention. Ordered HIGH -> LOW so that
     * comparing ordinal() values sorts higher-priority tasks first.
     */
    public enum Priority {
        HIGH, MEDIUM, LOW;

        /** Parses a form value like "High" (case-insensitive), defaulting to MEDIUM. */
        public static Priority fromString(String value) {
            if (value == null) {
                return MEDIUM;
            }
            try {
                return Priority.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return MEDIUM;
            }
        }

        /** Display label used in the badge, e.g. "High". */
        public String label() {
            return name().charAt(0) + name().substring(1).toLowerCase();
        }

        /** CSS class suffix used to color the badge for this priority. */
        public String cssClass() {
            return "priority-" + name().toLowerCase();
        }
    }

    private final int id;
    private final String description;
    private final Priority priority;
    private boolean done;

    public Task(int id, String description, Priority priority) {
        this.id = id;
        this.description = description;
        this.priority = priority;
        this.done = false;
    }

    public int getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public Priority getPriority() {
        return priority;
    }

    public boolean isDone() {
        return done;
    }

    public void markDone() {
        this.done = true;
    }
}
