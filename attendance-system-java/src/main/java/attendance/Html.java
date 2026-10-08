package attendance;

/** Page layout, HTML escaping and the stylesheet. */
final class Html {

    private Html() {}

    static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&#39;");
                default -> b.append(c);
            }
        }
        return b.toString();
    }

    static String page(String title, String body, String msg, String msgType) {
        String flash = "";
        if (msg != null && !msg.isBlank()) {
            String type = "error".equals(msgType) ? "error" : "success";
            String shown = msg.length() > 200 ? msg.substring(0, 200) : msg;
            flash = "<div class=\"flash " + type + "\">" + esc(shown) + "</div>";
        }
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%s</title>
                  <link rel="stylesheet" href="/static/style.css">
                </head>
                <body>
                  <nav>
                    <span class="brand">Student Attendance</span>
                    <a href="/">Dashboard</a>
                    <a href="/students">Students</a>
                    <a href="/attendance">Mark Attendance</a>
                    <a href="/report">Report</a>
                  </nav>
                  <main>
                    %s
                    %s
                  </main>
                </body>
                </html>
                """.formatted(esc(title), flash, body);
    }

    static final String CSS = """
            * { box-sizing: border-box; }
            body { margin: 0; font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; background: #f5f6f8; color: #1f2933; }
            nav { background: #1d3557; padding: 12px 24px; display: flex; gap: 20px; align-items: center; flex-wrap: wrap; }
            nav .brand { color: #fff; font-weight: 700; margin-right: 12px; }
            nav a { color: #cfe0f5; text-decoration: none; }
            nav a:hover { color: #fff; }
            main { max-width: 900px; margin: 24px auto; padding: 0 16px; }
            h1 { margin-top: 0; }
            .muted { color: #6b7785; }
            .cards { display: flex; gap: 16px; flex-wrap: wrap; margin: 16px 0; }
            .card { flex: 1; min-width: 160px; background: #fff; border-radius: 10px; padding: 20px; text-align: center; box-shadow: 0 1px 3px rgba(0,0,0,.1); }
            .card .num { font-size: 36px; font-weight: 700; }
            .card.present .num { color: #2a9d55; }
            .card.absent .num { color: #d64545; }
            table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 10px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,.1); margin: 16px 0; }
            th, td { padding: 10px 12px; text-align: left; border-bottom: 1px solid #e6e9ee; }
            th { background: #eef1f6; }
            td.low { color: #d64545; font-weight: 700; }
            .btn { background: #1d3557; color: #fff; border: 0; padding: 8px 14px; border-radius: 6px; cursor: pointer; text-decoration: none; display: inline-block; font-size: 14px; }
            .btn:hover { background: #274a7a; }
            .btn.danger { background: #d64545; }
            .inline-form { display: flex; gap: 8px; flex-wrap: wrap; margin: 16px 0; align-items: center; }
            input:not([type=radio]) { padding: 8px 10px; border: 1px solid #c5ccd6; border-radius: 6px; }
            .flash { padding: 10px 14px; border-radius: 6px; margin-bottom: 12px; }
            .flash.success { background: #dff5e7; color: #1b6b3a; }
            .flash.error { background: #fde3e3; color: #9b2c2c; }
            """;
}
