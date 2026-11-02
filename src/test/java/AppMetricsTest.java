import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AppMetricsTest.java
 *
 * Tests the request counters behind GET /health and checks that the JSON
 * document contains the fields the monitoring script (scripts/monitor.sh)
 * reads, so a change to AppMetrics can't silently break monitoring.
 */
class AppMetricsTest {

    @Test
    void recordRequest_countsRequestsAndSeparatesClientAndServerErrors() {
        AppMetrics metrics = new AppMetrics();

        metrics.recordRequest(200, 1_000_000);
        metrics.recordRequest(303, 1_000_000);
        metrics.recordRequest(404, 1_000_000);
        metrics.recordRequest(500, 1_000_000);

        assertEquals(4, metrics.requestCount());
        assertEquals(1, metrics.clientErrorCount());
        assertEquals(1, metrics.serverErrorCount());
    }

    @Test
    void averageAndMaxRequestTime_areReportedInMilliseconds() {
        AppMetrics metrics = new AppMetrics();

        metrics.recordRequest(200, 2_000_000);   // 2 ms
        metrics.recordRequest(200, 6_000_000);   // 6 ms

        assertEquals(4.0, metrics.averageRequestMillis(), 0.001);
        assertEquals(6.0, metrics.maxRequestMillis(), 0.001);
    }

    @Test
    void averageRequestTime_isZeroBeforeAnyRequests() {
        assertEquals(0.0, new AppMetrics().averageRequestMillis(), 0.0);
    }

    @Test
    void toJson_includesStatusTaskCountsAndJvmFieldsUsedByMonitorScript() {
        AppMetrics metrics = new AppMetrics();
        TaskStore store = new TaskStore();
        store.addTask("Task A", "HIGH");
        store.addTask("Task B", "LOW");
        store.completeTask(1);
        metrics.recordRequest(200, 1_000_000);

        String json = metrics.toJson(store);

        assertTrue(json.startsWith("{") && json.endsWith("}"));
        assertTrue(json.contains("\"status\":\"UP\""));
        assertTrue(json.contains("\"tasks\":{\"total\":2,\"active\":1,\"completed\":1}"), json);
        assertTrue(json.contains("\"requests\":{\"total\":1,"), json);
        for (String field : new String[] {"heap_used_mib", "heap_committed_mib", "heap_max_mib",
                "threads_live", "gc_collections", "avg_ms", "process_cpu_pct"}) {
            assertTrue(json.contains("\"" + field + "\":"), "missing " + field + " in " + json);
        }
    }
}
