import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TaskTrackerServer.java
 *
 * Wires up a tiny HTTP server (using only classes built into the JDK --
 * no external web framework) with four routes:
 *
 *   GET  /         renders the task list page
 *   POST /add      reads a "description" form field and adds a task
 *   POST /complete reads an "id" form field and marks that task done
 *   POST /delete   reads an "id" form field and removes that task
 *
 * All POST handlers redirect back to "/" when finished, so the browser
 * always ends up looking at the current state of the list (this also
 * means refreshing the page after a submit doesn't resubmit the form).
 *
 * Every handler is wrapped in a LoggedHandler, which gives each request
 * an id (req-1, req-2, ...), times it, logs the outcome at a level that
 * matches the HTTP status, and turns any unexpected exception into a
 * logged SEVERE entry plus an HTTP 500 instead of a silently dropped
 * connection.
 */
public class TaskTrackerServer {

    private static final Logger LOG = Logger.getLogger(AppLogging.ROOT + ".http");

    private final TaskStore store;
    private final AtomicLong requestCounter = new AtomicLong();

    public TaskTrackerServer(TaskStore store) {
        this.store = store;
    }

    public HttpServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new LoggedHandler(new RootHandler()));
        server.createContext("/add", new LoggedHandler(new AddHandler()));
        server.createContext("/complete", new LoggedHandler(new CompleteHandler()));
        server.createContext("/delete", new LoggedHandler(new DeleteHandler()));
        server.setExecutor(null); // default single-threaded executor is fine for this app
        server.start();
        LOG.config("HTTP server listening on port " + port + " with routes /, /add, /complete, /delete");
        return server;
    }

    // ---- Request logging ----------------------------------------------

    /**
     * Wraps a route handler with request logging and error handling.
     *   2xx/3xx -> INFO     (normal traffic)
     *   4xx     -> WARNING  (client sent something we could not honor)
     *   5xx / exception -> SEVERE with the full stack trace
     */
    private class LoggedHandler implements HttpHandler {
        private final HttpHandler delegate;

        LoggedHandler(HttpHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String reqId = "req-" + requestCounter.incrementAndGet();
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String client = exchange.getRemoteAddress() == null
                    ? "unknown" : exchange.getRemoteAddress().getAddress().getHostAddress();
            long start = System.nanoTime();
            LOG.fine(reqId + " -> " + method + " " + path + " from " + client
                    + " user-agent=\"" + exchange.getRequestHeaders().getFirst("User-Agent") + "\"");
            try {
                delegate.handle(exchange);
            } catch (Exception e) {
                LOG.log(Level.SEVERE, reqId + " " + method + " " + path + " from " + client
                        + " failed with " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
                sendError(exchange, reqId);
            } finally {
                long ms = (System.nanoTime() - start) / 1_000_000;
                int status = exchange.getResponseCode();
                String line = reqId + " " + method + " " + path + " -> " + status + " in " + ms + " ms from " + client;
                if (status >= 500) {
                    LOG.severe(line);
                } else if (status >= 400) {
                    LOG.warning(line);
                } else {
                    LOG.info(line);
                }
                exchange.close();
            }
        }

        private void sendError(HttpExchange exchange, String reqId) {
            try {
                if (exchange.getResponseCode() == -1) { // headers not sent yet
                    sendHtml(exchange, 500, "<h1>500 - Something went wrong</h1><p>Reference: "
                            + reqId + " (see the server log)</p><p><a href='/'>Back to Task Tracker</a></p>");
                }
            } catch (IOException ioe) {
                LOG.log(Level.WARNING, reqId + " could not send 500 response to client", ioe);
            }
        }
    }

    // ---- Handlers ---------------------------------------------------

    private class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            if (!"/".equals(exchange.getRequestURI().getPath())) {
                sendHtml(exchange, 404, "<h1>404 - Not found</h1><p><a href='/'>Back to Task Tracker</a></p>");
                return;
            }
            List<Task> tasks = store.getAllTasksByPriority();
            String html = renderPage(tasks, store.countActive(), store.countCompleted());
            LOG.fine("Rendered task list: " + tasks.size() + " tasks, " + html.length() + " bytes");
            sendHtml(exchange, 200, html);
        }
    }

    private class AddHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            Map<String, String> form = parseFormBody(exchange);
            LOG.fine("Add form fields: description=\"" + form.get("description")
                    + "\" priority=" + form.get("priority"));
            store.addTask(form.get("description"), form.get("priority"));
            redirectHome(exchange);
        }
    }

    private class CompleteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            Map<String, String> form = parseFormBody(exchange);
            try {
                int id = Integer.parseInt(form.get("id"));
                store.completeTask(id);
            } catch (NumberFormatException e) {
                // Malformed id: nothing to complete, redirect home with no change.
                LOG.warning("Complete ignored: malformed id '" + form.get("id") + "' (" + e.getMessage() + ")");
            }
            redirectHome(exchange);
        }
    }

    private class DeleteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            Map<String, String> form = parseFormBody(exchange);
            try {
                int id = Integer.parseInt(form.get("id"));
                store.deleteTask(id);
            } catch (NumberFormatException e) {
                // Malformed id: nothing to delete, redirect home with no change.
                LOG.warning("Delete ignored: malformed id '" + form.get("id") + "' (" + e.getMessage() + ")");
            }
            redirectHome(exchange);
        }
    }

    // ---- HTML rendering ----------------------------------------------

    private String renderPage(List<Task> tasks, long active, long completed) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset='utf-8'>");
        html.append("<title>Task Tracker</title>");
        html.append("<style>");
        html.append("body{font-family:Arial,Helvetica,sans-serif;max-width:640px;margin:40px auto;padding:0 16px;background:#f5f6f8;color:#222;}");
        html.append("h1{color:#2a4d69;}");
        html.append(".summary{color:#555;margin-bottom:16px;}");
        html.append("form.add-form{display:flex;gap:8px;margin-bottom:24px;}");
        html.append("input[type=text]{flex:1;padding:8px;font-size:15px;border:1px solid #c9ccd1;border-radius:4px;}");
        html.append("button{padding:8px 16px;font-size:15px;background:#2a4d69;color:white;border:none;border-radius:4px;cursor:pointer;}");
        html.append("button:hover{background:#1e3a52;}");
        html.append("ul{list-style:none;padding:0;}");
        html.append("li{background:white;border:1px solid #e0e2e6;border-radius:4px;padding:10px 14px;margin-bottom:8px;display:flex;justify-content:space-between;align-items:center;}");
        html.append("li.done{opacity:0.6;}");
        html.append("li.done .desc{text-decoration:line-through;}");
        html.append(".complete-btn{background:#3f9142;padding:4px 10px;font-size:13px;}");
        html.append(".complete-btn:hover{background:#347a37;}");
        html.append(".delete-btn{background:#b03a2e;padding:4px 10px;font-size:13px;}");
        html.append(".delete-btn:hover{background:#8e2e24;}");
        html.append("select{padding:8px;font-size:15px;border:1px solid #c9ccd1;border-radius:4px;background:white;}");
        html.append(".left{display:flex;align-items:center;gap:10px;}");
        html.append(".badge{font-size:11px;font-weight:bold;text-transform:uppercase;padding:3px 8px;border-radius:10px;color:white;}");
        html.append(".priority-high{background:#d64545;}");
        html.append(".priority-medium{background:#d99a3a;}");
        html.append(".priority-low{background:#4a90d9;}");
        html.append("</style></head><body>");
        html.append("<h1>Task Tracker</h1>");
        html.append("<p class='summary'>").append(active).append(" active, ").append(completed).append(" completed</p>");

        html.append("<form class='add-form' action='/add' method='post'>");
        html.append("<input type='text' name='description' placeholder='What do you need to do?' required>");
        html.append("<select name='priority'>");
        html.append("<option value='HIGH'>High</option>");
        html.append("<option value='MEDIUM' selected>Medium</option>");
        html.append("<option value='LOW'>Low</option>");
        html.append("</select>");
        html.append("<button type='submit'>Add</button>");
        html.append("</form>");

        html.append("<ul>");
        for (Task t : tasks) {
            html.append("<li class='").append(t.isDone() ? "done" : "").append("'>");
            html.append("<span class='left'>");
            html.append("<span class='badge ").append(t.getPriority().cssClass()).append("'>")
                    .append(t.getPriority().label()).append("</span>");
            html.append("<span class='desc'>").append(escapeHtml(t.getDescription())).append("</span>");
            html.append("</span>");
            html.append("<span style='display:flex;gap:6px;'>");
            if (!t.isDone()) {
                html.append("<form action='/complete' method='post' style='margin:0;'>");
                html.append("<input type='hidden' name='id' value='").append(t.getId()).append("'>");
                html.append("<button class='complete-btn' type='submit'>Complete</button>");
                html.append("</form>");
            }
            html.append("<form action='/delete' method='post' style='margin:0;'>");
            html.append("<input type='hidden' name='id' value='").append(t.getId()).append("'>");
            html.append("<button class='delete-btn' type='submit'>Delete</button>");
            html.append("</form>");
            html.append("</span>");
            html.append("</li>");
        }
        if (tasks.isEmpty()) {
            html.append("<li>No tasks yet -- add one above.</li>");
        }
        html.append("</ul>");

        html.append("<p style='margin-top:24px;color:#999;font-size:12px;'>Task Tracker v1.2 (logging)</p>");
        html.append("</body></html>");
        return html.toString();
    }

    // ---- Small HTTP helpers -------------------------------------------

    private void sendHtml(HttpExchange exchange, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void redirectHome(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Location", "/");
        exchange.sendResponseHeaders(303, -1);
    }

    private Map<String, String> parseFormBody(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> result = new HashMap<>();
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            result.put(key, value);
        }
        return result;
    }

    private String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}