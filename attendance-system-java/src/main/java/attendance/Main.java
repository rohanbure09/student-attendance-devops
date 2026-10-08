package attendance;

import java.nio.file.Path;
import java.nio.file.Paths;

/** Entry point. Configure with the PORT and DATA_DIR environment variables. */
public final class Main {

    private Main() {}

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(env("PORT", "8080"));
        Path dataDir = Paths.get(env("DATA_DIR", "data"));

        Store store = new Store(dataDir.resolve("attendance-data.tsv"));
        AttendanceServer app = new AttendanceServer(store);
        app.start(port);
        Runtime.getRuntime().addShutdownHook(new Thread(app::stop));

        System.out.println("Student Attendance System running on http://localhost:" + port);
        System.out.println("Data file: " + dataDir.toAbsolutePath().resolve("attendance-data.tsv"));
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
