import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * AppMetrics.java
 *
 * Collects simple operational measurements for the Task Tracker and
 * reports them, together with JVM runtime measurements, as JSON on the
 * GET /health endpoint.
 *
 * Docker (docker stats) and the operating system can only see the app
 * from the outside: total CPU % and total memory of the container or
 * process. These numbers show what is happening inside: how much of that
 * memory is actually Java heap in use, how many requests have been
 * served, how long they take, and how often garbage collection runs.
 *
 * Uses only java.lang.management (built into the JDK), so the project
 * still has no runtime dependencies.
 */
public class AppMetrics {

    private static final double MIB = 1024.0 * 1024.0;

    private final long startMillis = System.currentTimeMillis();
    private final LongAdder requests = new LongAdder();
    private final LongAdder clientErrors = new LongAdder();   // 4xx
    private final LongAdder serverErrors = new LongAdder();   // 5xx
    private final LongAdder totalNanos = new LongAdder();
    private final AtomicLong maxNanos = new AtomicLong();

    /** Records one finished application request (not /health itself). */
    public void recordRequest(int status, long durationNanos) {
        requests.increment();
        totalNanos.add(durationNanos);
        maxNanos.accumulateAndGet(durationNanos, Math::max);
        if (status >= 500) {
            serverErrors.increment();
        } else if (status >= 400) {
            clientErrors.increment();
        }
    }

    public long requestCount() {
        return requests.sum();
    }

    public long serverErrorCount() {
        return serverErrors.sum();
    }

    public long clientErrorCount() {
        return clientErrors.sum();
    }

    /** Average request time in milliseconds, or 0 if nothing was served yet. */
    public double averageRequestMillis() {
        long n = requests.sum();
        return n == 0 ? 0.0 : totalNanos.sum() / 1_000_000.0 / n;
    }

    public double maxRequestMillis() {
        return maxNanos.get() / 1_000_000.0;
    }

    /**
     * Builds the JSON document returned by GET /health. Units are in the
     * field names: _mib = mebibytes, _ms = milliseconds, _s = seconds.
     */
    public String toJson(TaskStore store) {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        long gcCount = 0;
        long gcMillis = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += Math.max(0, gc.getCollectionCount());
            gcMillis += Math.max(0, gc.getCollectionTime());
        }
        double processCpu = processCpuLoad();

        StringBuilder json = new StringBuilder("{");
        field(json, "status", "UP");
        field(json, "uptime_s", (System.currentTimeMillis() - startMillis) / 1000);
        json.append("\"tasks\":{");
        field(json, "total", store.getAllTasks().size());
        field(json, "active", store.countActive());
        lastField(json, "completed", store.countCompleted());
        json.append("},\"requests\":{");
        field(json, "total", requestCount());
        field(json, "client_errors_4xx", clientErrorCount());
        field(json, "server_errors_5xx", serverErrorCount());
        field(json, "avg_ms", round(averageRequestMillis()));
        lastField(json, "max_ms", round(maxRequestMillis()));
        json.append("},\"jvm\":{");
        field(json, "heap_used_mib", round(heap.getUsed() / MIB));
        field(json, "heap_committed_mib", round(heap.getCommitted() / MIB));
        field(json, "heap_max_mib", round(heap.getMax() / MIB));
        field(json, "nonheap_used_mib", round(nonHeap.getUsed() / MIB));
        field(json, "threads_live", threads.getThreadCount());
        field(json, "threads_peak", threads.getPeakThreadCount());
        field(json, "gc_collections", gcCount);
        field(json, "gc_time_ms", gcMillis);
        field(json, "process_cpu_pct", processCpu < 0 ? -1 : round(processCpu * 100));
        lastField(json, "available_processors", Runtime.getRuntime().availableProcessors());
        json.append("}}");
        return json.toString();
    }

    /** JVM-reported CPU load of this process (0.0-1.0), or -1 if unavailable. */
    private static double processCpuLoad() {
        java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof com.sun.management.OperatingSystemMXBean) {
            return ((com.sun.management.OperatingSystemMXBean) os).getProcessCpuLoad();
        }
        return -1;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static void field(StringBuilder json, String name, Object value) {
        appendValue(json, name, value);
        json.append(',');
    }

    private static void lastField(StringBuilder json, String name, Object value) {
        appendValue(json, name, value);
    }

    private static void appendValue(StringBuilder json, String name, Object value) {
        json.append('"').append(name).append("\":");
        if (value instanceof String) {
            json.append('"').append(value).append('"');
        } else if (value instanceof Double) {
            json.append(String.format(Locale.ROOT, "%.2f", (Double) value));
        } else {
            json.append(value);
        }
    }
}
