package attendance;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** HTTP routes, built on the JDK's own web server (no external libraries). */
final class AttendanceServer {

    private static final Pattern DELETE_PATH = Pattern.compile("^/students/(\\d{1,9})/delete$");
    private static final int MAX_BODY_BYTES = 1_000_000;
    private static final String HTML = "text/html; charset=utf-8";

    private final Store store;
    private HttpServer server;

    AttendanceServer(Store store) {
        this.store = store;
    }

    HttpServer start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        return server;
    }

    void stop() {
        if (server != null) {
            server.stop(0);
            if (server.getExecutor() instanceof ExecutorService pool) {
                pool.shutdown();
            }
        }
    }

    // ---------- dispatch ----------

    private void handle(HttpExchange ex) throws IOException {
        try {
            route(ex);
        } catch (Exception e) {
            e.printStackTrace();
            try {
                send(ex, 500, "text/plain; charset=utf-8", "Internal server error");
            } catch (IOException ignored) {
                // response was already started; nothing more to do
            }
        } finally {
            ex.close();
        }
    }

    private void route(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        Map<String, String> query = parseForm(ex.getRequestURI().getRawQuery());
        boolean get = method.equals("GET");
        boolean post = method.equals("POST");

        switch (path) {
            case "/" -> {
                if (!get) { notAllowed(ex); return; }
                dashboard(ex, query);
            }
            case "/students" -> {
                if (get) { studentsPage(ex, query); }
                else if (post) { addStudent(ex); }
                else { notAllowed(ex); }
            }
            case "/attendance" -> {
                if (get) { attendancePage(ex, query); }
                else if (post) { saveAttendance(ex, query); }
                else { notAllowed(ex); }
            }
            case "/report" -> {
                if (!get) { notAllowed(ex); return; }
                send(ex, 200, HTML, Html.page("Report", Pages.report(store.report()), null, null));
            }
            case "/health" -> {
                if (!get) { notAllowed(ex); return; }
                send(ex, 200, "application/json", "{\"status\":\"ok\"}");
            }
            case "/api/students" -> {
                if (!get) { notAllowed(ex); return; }
                send(ex, 200, "application/json", studentsJson());
            }
            case "/static/style.css" -> {
                if (!get) { notAllowed(ex); return; }
                send(ex, 200, "text/css; charset=utf-8", Html.CSS);
            }
            default -> {
                Matcher m = DELETE_PATH.matcher(path);
                if (m.matches()) {
                    if (!post) { notAllowed(ex); return; }
                    store.deleteStudent(Integer.parseInt(m.group(1)));
                    redirect(ex, "/students" + flash("Student deleted.", "success", null));
                } else {
                    send(ex, 404, HTML, Html.page("Not found",
                            "<h1>404</h1><p>Page not found. <a href=\"/\">Back to dashboard</a></p>", null, null));
                }
            }
        }
    }

    // ---------- handlers ----------

    private void dashboard(HttpExchange ex, Map<String, String> query) throws IOException {
        String today = LocalDate.now().toString();
        String body = Pages.dashboard(store.studentCount(),
                store.count(today, "Present"), store.count(today, "Absent"), today);
        send(ex, 200, HTML, Html.page("Dashboard", body, query.get("msg"), query.get("type")));
    }

    private void studentsPage(HttpExchange ex, Map<String, String> query) throws IOException {
        send(ex, 200, HTML, Html.page("Students", Pages.students(store.listStudents()),
                query.get("msg"), query.get("type")));
    }

    private void addStudent(HttpExchange ex) throws IOException {
        Map<String, String> form = readForm(ex);
        String error = store.addStudent(form.get("roll_no"), form.get("name"), form.get("class_name"));
        if (error != null) {
            redirect(ex, "/students" + flash(error, "error", null));
        } else {
            String name = form.get("name").strip();
            redirect(ex, "/students" + flash("Student " + name + " added.", "success", null));
        }
    }

    private void attendancePage(HttpExchange ex, Map<String, String> query) throws IOException {
        String msg = query.get("msg");
        String type = query.get("type");
        String date = parseDate(query.get("date"));
        if (date == null) {
            date = LocalDate.now().toString();
            msg = "Invalid date.";
            type = "error";
        }
        String body = Pages.attendance(store.listStudents(), store.statusesOn(date), date);
        send(ex, 200, HTML, Html.page("Mark Attendance", body, msg, type));
    }

    private void saveAttendance(HttpExchange ex, Map<String, String> query) throws IOException {
        Map<String, String> form = readForm(ex);
        String raw = form.get("date") != null ? form.get("date") : query.get("date");
        String date = parseDate(raw);
        if (date == null) {
            redirect(ex, "/attendance" + flash("Invalid date.", "error", null));
            return;
        }
        Map<Integer, String> statuses = new HashMap<>();
        for (Store.Student s : store.listStudents()) {
            String status = form.get("status_" + s.id());
            if (status != null) {
                statuses.put(s.id(), status);
            }
        }
        store.saveAttendance(date, statuses);
        redirect(ex, "/attendance" + flash("Attendance saved for " + date + ".", "success", date));
    }

    // ---------- helpers ----------

    /** Returns the date in ISO form (yyyy-MM-dd), or null when missing/invalid; blank means today. */
    private static String parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDate.now().toString();
        }
        try {
            return LocalDate.parse(raw.strip()).toString();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String studentsJson() {
        StringBuilder b = new StringBuilder("[");
        boolean first = true;
        for (Store.Student s : store.listStudents()) {
            if (!first) {
                b.append(',');
            }
            first = false;
            b.append("{\"id\":").append(s.id())
                    .append(",\"roll_no\":\"").append(json(s.rollNo()))
                    .append("\",\"name\":\"").append(json(s.name()))
                    .append("\",\"class_name\":\"").append(json(s.className()))
                    .append("\"}");
        }
        return b.append(']').toString();
    }

    private static String json(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.toString();
    }

    private static String flash(String msg, String type, String date) {
        StringBuilder q = new StringBuilder("?");
        if (date != null) {
            q.append("date=").append(URLEncoder.encode(date, StandardCharsets.UTF_8)).append('&');
        }
        q.append("msg=").append(URLEncoder.encode(msg, StandardCharsets.UTF_8));
        q.append("&type=").append(type);
        return q.toString();
    }

    private static Map<String, String> readForm(HttpExchange ex) throws IOException {
        byte[] body = ex.getRequestBody().readNBytes(MAX_BODY_BYTES);
        return parseForm(new String(body, StandardCharsets.UTF_8));
    }

    static Map<String, String> parseForm(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return map;
        }
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int i = pair.indexOf('=');
            String key = i < 0 ? pair : pair.substring(0, i);
            String value = i < 0 ? "" : pair.substring(i + 1);
            map.put(decode(key), decode(value));
        }
        return map;
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return s;
        }
    }

    private static void redirect(HttpExchange ex, String location) throws IOException {
        ex.getResponseHeaders().set("Location", location);
        ex.sendResponseHeaders(303, -1);
    }

    private static void notAllowed(HttpExchange ex) throws IOException {
        send(ex, 405, "text/plain; charset=utf-8", "Method not allowed");
    }

    private static void send(HttpExchange ex, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
