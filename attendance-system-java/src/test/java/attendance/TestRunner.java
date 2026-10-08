package attendance;

import com.sun.net.httpserver.HttpServer;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Tiny dependency-free test runner (so the project builds with only the JDK).
 * Every test gets a fresh server and an empty data file. Exit code 1 on failure.
 */
public final class TestRunner {

    private interface TestBody {
        void run(Env env) throws Exception;
    }

    private record Resp(int status, String body, String location) {
        String decodedLocation() {
            return location == null ? "" : URLDecoder.decode(location, StandardCharsets.UTF_8);
        }
    }

    private static final class Env {
        private final HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER).build();
        private final String base;

        Env(String base) {
            this.base = base;
        }

        Resp get(String path) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + path)).GET().build();
            return toResp(client.send(request, HttpResponse.BodyHandlers.ofString()));
        }

        Resp post(String path, String... keyValues) throws Exception {
            StringBuilder form = new StringBuilder();
            for (int i = 0; i < keyValues.length; i += 2) {
                if (i > 0) {
                    form.append('&');
                }
                form.append(URLEncoder.encode(keyValues[i], StandardCharsets.UTF_8))
                        .append('=')
                        .append(URLEncoder.encode(keyValues[i + 1], StandardCharsets.UTF_8));
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + path))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                    .build();
            return toResp(client.send(request, HttpResponse.BodyHandlers.ofString()));
        }

        void addStudent(String roll, String name, String cls) throws Exception {
            post("/students", "roll_no", roll, "name", name, "class_name", cls);
        }

        private static Resp toResp(HttpResponse<String> r) {
            return new Resp(r.statusCode(), r.body(), r.headers().firstValue("Location").orElse(null));
        }
    }

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        test("health endpoint", env -> {
            Resp r = env.get("/health");
            check(r.status() == 200, "status 200, got " + r.status());
            check(r.body().contains("\"status\":\"ok\""), "body: " + r.body());
            check(env.post("/health").status() == 405, "POST /health should be 405");
        });

        test("all pages load", env -> {
            for (String url : new String[] {"/", "/students", "/attendance", "/report", "/static/style.css", "/api/students"}) {
                check(env.get(url).status() == 200, url + " should return 200");
            }
            check(env.get("/does-not-exist").status() == 404, "unknown path should be 404");
        });

        test("add student, flash message and duplicate roll number", env -> {
            Resp added = env.post("/students", "roll_no", "1", "name", "Asha", "class_name", "10-A");
            check(added.status() == 303, "add should redirect, got " + added.status());
            check(env.get(added.location()).body().contains("Student Asha added."), "success flash missing");

            Resp dup = env.post("/students", "roll_no", "1", "name", "Other", "class_name", "10-A");
            check(dup.decodedLocation().contains("already exists"), "duplicate not rejected: " + dup.decodedLocation());

            Resp empty = env.post("/students", "roll_no", "", "name", "X", "class_name", "10-A");
            check(empty.decodedLocation().contains("required"), "empty field not rejected");

            String api = env.get("/api/students").body();
            check(api.contains("\"roll_no\":\"1\"") && api.contains("\"name\":\"Asha\""), "api: " + api);
            check(api.indexOf("\"id\":", api.indexOf("\"id\":") + 1) < 0, "only one student expected: " + api);
        });

        test("mark attendance and report percentages", env -> {
            env.addStudent("1", "Asha", "10-A");
            env.addStudent("2", "Ravi", "10-A");
            check(env.post("/attendance", "date", "2026-10-01", "status_1", "Present", "status_2", "Absent").status() == 303, "save should redirect");
            env.post("/attendance", "date", "2026-10-02", "status_1", "Present", "status_2", "Present");
            String html = env.get("/report").body();
            check(html.contains("100.0%"), "Asha should be 100.0%");
            check(html.contains("50.0%"), "Ravi should be 50.0%");
        });

        test("re-saving the same day updates it", env -> {
            env.addStudent("1", "Asha", "10-A");
            env.post("/attendance", "date", "2026-10-01", "status_1", "Absent");
            env.post("/attendance", "date", "2026-10-01", "status_1", "Present");
            String html = env.get("/report").body();
            check(html.contains("100.0%"), "should be 100.0% after update");
            check(!html.contains("50.0%"), "should not have a duplicate day");
        });

        test("attendance page shows saved status", env -> {
            env.addStudent("1", "Asha", "10-A");
            env.post("/attendance", "date", "2026-10-01", "status_1", "Present");
            String html = env.get("/attendance?date=2026-10-01").body();
            check(html.contains("value=\"Present\" checked"), "Present radio should be checked");
        });

        test("deleting a student removes their attendance", env -> {
            env.addStudent("1", "Asha", "10-A");
            env.post("/attendance", "date", "2026-10-01", "status_1", "Present");
            check(env.post("/students/1/delete").status() == 303, "delete should redirect");
            check(env.get("/api/students").body().equals("[]"), "student list should be empty");
            env.addStudent("1", "Asha", "10-A");
            String html = env.get("/report").body();
            check(html.contains("0.0%") && !html.contains("100.0%"), "old attendance must not come back");
        });

        test("invalid date is rejected without crashing", env -> {
            env.addStudent("1", "Asha", "10-A");
            Resp r = env.post("/attendance", "date", "garbage", "status_1", "Present");
            check(r.status() == 303 && r.decodedLocation().contains("Invalid date"), "bad date on save");
            Resp page = env.get("/attendance?date=garbage");
            check(page.status() == 200 && page.body().contains("Invalid date."), "bad date on view");
        });

        test("HTML is escaped (no script injection)", env -> {
            env.addStudent("1", "<script>alert(1)</script>", "10-A");
            String html = env.get("/students").body();
            check(!html.contains("<script>alert(1)"), "raw script tag leaked into page");
            check(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"), "escaped text missing");
        });

        test("numeric roll numbers sort naturally (2, 9, 10)", env -> {
            env.addStudent("10", "C", "10-A");
            env.addStudent("9", "B", "10-A");
            env.addStudent("2", "A", "10-A");
            String api = env.get("/api/students").body();
            int a = api.indexOf("\"roll_no\":\"2\"");
            int b = api.indexOf("\"roll_no\":\"9\"");
            int c = api.indexOf("\"roll_no\":\"10\"");
            check(a >= 0 && a < b && b < c, "order wrong: " + api);
        });

        test("data survives a restart", env -> {
            Path dir = Files.createTempDirectory("attendance-persist");
            try {
                Path file = dir.resolve("nested/data.tsv");
                Store first = new Store(file);
                check(first.addStudent("1", "Tab\tName\\x", "10-A") == null, "add failed");
                check(first.addStudent("1", "Dup", "10-A") != null, "duplicate allowed");
                first.saveAttendance("2026-10-01", Map.of(1, "Present"));

                Store second = new Store(file);
                check(second.listStudents().size() == 1, "student lost");
                check(second.listStudents().get(0).name().equals("Tab Name\\x"), "name changed: " + second.listStudents().get(0).name());
                Store.ReportRow row = second.report().get(0);
                check(row.totalDays() == 1 && row.presentDays() == 1, "attendance lost");
                check(second.addStudent("2", "Next", "10-A") == null, "add after reload failed");
                check(second.listStudents().get(1).id() == 2, "ids should continue after reload");
            } finally {
                deleteTree(dir);
            }
        });

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    private static void test(String name, TestBody body) {
        Path dir = null;
        AttendanceServer app = null;
        try {
            dir = Files.createTempDirectory("attendance-test");
            app = new AttendanceServer(new Store(dir.resolve("data.tsv")));
            HttpServer server = app.start(0);
            body.run(new Env("http://127.0.0.1:" + server.getAddress().getPort()));
            passed++;
            System.out.println("PASS  " + name);
        } catch (Throwable t) {
            failed++;
            System.out.println("FAIL  " + name + "  ->  " + t);
        } finally {
            if (app != null) {
                app.stop();
            }
            if (dir != null) {
                deleteTree(dir);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void deleteTree(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (Exception ignored) {
            // best-effort cleanup of temp files
        }
    }
}
