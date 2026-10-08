package attendance;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The HTML bodies of each page (the layout is added by {@link Html#page}). */
final class Pages {

    private Pages() {}

    static String dashboard(int total, int present, int absent, String today) {
        return """
                <h1>Dashboard</h1>
                <p class="muted">Today: %s</p>
                <div class="cards">
                  <div class="card"><div class="num">%d</div><div>Total students</div></div>
                  <div class="card present"><div class="num">%d</div><div>Present today</div></div>
                  <div class="card absent"><div class="num">%d</div><div>Absent today</div></div>
                </div>
                <p><a class="btn" href="/attendance">Mark today's attendance</a></p>
                """.formatted(Html.esc(today), total, present, absent);
    }

    static String students(List<Store.Student> students) {
        StringBuilder b = new StringBuilder();
        b.append("""
                <h1>Students</h1>
                <form method="post" action="/students" class="inline-form">
                  <input name="roll_no" placeholder="Roll no" maxlength="100" required>
                  <input name="name" placeholder="Full name" maxlength="100" required>
                  <input name="class_name" placeholder="Class (e.g. 10-A)" maxlength="100" required>
                  <button class="btn" type="submit">Add student</button>
                </form>
                <table>
                  <thead><tr><th>Roll no</th><th>Name</th><th>Class</th><th></th></tr></thead>
                  <tbody>
                """);
        if (students.isEmpty()) {
            b.append("<tr><td colspan=\"4\" class=\"muted\">No students yet. Add one above.</td></tr>\n");
        }
        for (Store.Student s : students) {
            b.append("<tr><td>").append(Html.esc(s.rollNo()))
                    .append("</td><td>").append(Html.esc(s.name()))
                    .append("</td><td>").append(Html.esc(s.className()))
                    .append("</td><td><form method=\"post\" action=\"/students/").append(s.id())
                    .append("/delete\" onsubmit=\"return confirm('Delete this student and their attendance?');\">")
                    .append("<button class=\"btn danger\" type=\"submit\">Delete</button></form></td></tr>\n");
        }
        b.append("</tbody></table>");
        return b.toString();
    }

    static String attendance(List<Store.Student> students, Map<Integer, String> statuses, String date) {
        StringBuilder b = new StringBuilder();
        b.append("<h1>Mark Attendance</h1>");
        b.append("<form method=\"get\" action=\"/attendance\" class=\"inline-form\">")
                .append("<label>Date <input type=\"date\" name=\"date\" value=\"").append(Html.esc(date)).append("\"></label>")
                .append("<button class=\"btn\" type=\"submit\">Load</button></form>");
        if (students.isEmpty()) {
            b.append("<p class=\"muted\">No students yet. <a href=\"/students\">Add students first.</a></p>");
            return b.toString();
        }
        b.append("<form method=\"post\" action=\"/attendance\">")
                .append("<input type=\"hidden\" name=\"date\" value=\"").append(Html.esc(date)).append("\">")
                .append("<table><thead><tr><th>Roll no</th><th>Name</th><th>Class</th><th>Present</th><th>Absent</th></tr></thead><tbody>\n");
        for (Store.Student s : students) {
            boolean present = "Present".equals(statuses.get(s.id()));
            b.append("<tr><td>").append(Html.esc(s.rollNo()))
                    .append("</td><td>").append(Html.esc(s.name()))
                    .append("</td><td>").append(Html.esc(s.className()))
                    .append("</td><td><input type=\"radio\" name=\"status_").append(s.id())
                    .append("\" value=\"Present\"").append(present ? " checked" : "")
                    .append("></td><td><input type=\"radio\" name=\"status_").append(s.id())
                    .append("\" value=\"Absent\"").append(present ? "" : " checked")
                    .append("></td></tr>\n");
        }
        b.append("</tbody></table><button class=\"btn\" type=\"submit\">Save attendance</button></form>");
        return b.toString();
    }

    static String report(List<Store.ReportRow> rows) {
        StringBuilder b = new StringBuilder();
        b.append("""
                <h1>Attendance Report</h1>
                <table>
                  <thead><tr><th>Roll no</th><th>Name</th><th>Class</th><th>Days recorded</th><th>Present</th><th>Attendance %</th></tr></thead>
                  <tbody>
                """);
        if (rows.isEmpty()) {
            b.append("<tr><td colspan=\"6\" class=\"muted\">No data yet.</td></tr>\n");
        }
        for (Store.ReportRow r : rows) {
            double pct = r.percentage();
            String cls = r.totalDays() > 0 && pct < 75 ? "low" : "";
            Store.Student s = r.student();
            b.append("<tr><td>").append(Html.esc(s.rollNo()))
                    .append("</td><td>").append(Html.esc(s.name()))
                    .append("</td><td>").append(Html.esc(s.className()))
                    .append("</td><td>").append(r.totalDays())
                    .append("</td><td>").append(r.presentDays())
                    .append("</td><td class=\"").append(cls).append("\">")
                    .append(String.format(Locale.ROOT, "%.1f%%", pct))
                    .append("</td></tr>\n");
        }
        b.append("</tbody></table><p class=\"muted\">Percentages below 75% are highlighted.</p>");
        return b.toString();
    }
}
