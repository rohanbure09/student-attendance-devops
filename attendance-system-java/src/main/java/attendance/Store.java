package attendance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Thread-safe data store. Everything is kept in memory and written to a small
 * tab-separated text file after every change, so data survives restarts.
 */
final class Store {

    record Student(int id, String rollNo, String name, String className) {}

    record ReportRow(Student student, int totalDays, int presentDays) {
        double percentage() {
            if (totalDays == 0) {
                return 0.0;
            }
            return Math.round(presentDays * 1000.0 / totalDays) / 10.0;
        }
    }

    static final int MAX_FIELD_LENGTH = 100;

    private static final Comparator<Student> ORDER = Comparator
            .comparing(Student::className)
            .thenComparing((a, b) -> compareRoll(a.rollNo(), b.rollNo()))
            .thenComparingInt(Student::id);

    private final Path file;
    private final List<Student> students = new ArrayList<>();
    /** studentId -> (date -> "Present" | "Absent") */
    private final Map<Integer, Map<String, String>> attendance = new HashMap<>();
    private int nextId = 1;

    Store(Path file) {
        this.file = file.toAbsolutePath();
        try {
            Files.createDirectories(this.file.getParent());
            if (Files.exists(this.file)) {
                load();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot use data file " + this.file, e);
        }
    }

    // ---------- queries ----------

    synchronized List<Student> listStudents() {
        List<Student> copy = new ArrayList<>(students);
        copy.sort(ORDER);
        return copy;
    }

    synchronized int studentCount() {
        return students.size();
    }

    synchronized Map<Integer, String> statusesOn(String date) {
        Map<Integer, String> result = new HashMap<>();
        for (Student s : students) {
            Map<String, String> byDate = attendance.get(s.id());
            if (byDate != null && byDate.containsKey(date)) {
                result.put(s.id(), byDate.get(date));
            }
        }
        return result;
    }

    synchronized int count(String date, String status) {
        int n = 0;
        for (Student s : students) {
            Map<String, String> byDate = attendance.get(s.id());
            if (byDate != null && status.equals(byDate.get(date))) {
                n++;
            }
        }
        return n;
    }

    synchronized List<ReportRow> report() {
        List<ReportRow> rows = new ArrayList<>();
        for (Student s : listStudents()) {
            Map<String, String> byDate = attendance.getOrDefault(s.id(), Map.of());
            int present = 0;
            for (String status : byDate.values()) {
                if ("Present".equals(status)) {
                    present++;
                }
            }
            rows.add(new ReportRow(s, byDate.size(), present));
        }
        return rows;
    }

    // ---------- commands ----------

    /** Returns an error message, or null when the student was added. */
    synchronized String addStudent(String rollNo, String name, String className) {
        rollNo = clean(rollNo);
        name = clean(name);
        className = clean(className);
        if (rollNo.isEmpty() || name.isEmpty() || className.isEmpty()) {
            return "All fields are required.";
        }
        if (rollNo.length() > MAX_FIELD_LENGTH || name.length() > MAX_FIELD_LENGTH
                || className.length() > MAX_FIELD_LENGTH) {
            return "Each field must be at most " + MAX_FIELD_LENGTH + " characters.";
        }
        for (Student s : students) {
            if (s.rollNo().equals(rollNo)) {
                return "Roll number " + rollNo + " already exists.";
            }
        }
        students.add(new Student(nextId++, rollNo, name, className));
        save();
        return null;
    }

    synchronized void deleteStudent(int id) {
        if (students.removeIf(s -> s.id() == id)) {
            attendance.remove(id);
            save();
        }
    }

    /** Saves a status for every student; anything other than "Present" counts as Absent. */
    synchronized void saveAttendance(String date, Map<Integer, String> statuses) {
        for (Student s : students) {
            String status = "Present".equals(statuses.get(s.id())) ? "Present" : "Absent";
            attendance.computeIfAbsent(s.id(), k -> new TreeMap<>()).put(date, status);
        }
        save();
    }

    // ---------- persistence ----------

    private void load() throws IOException {
        int lineNo = 0;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            lineNo++;
            if (line.isBlank()) {
                continue;
            }
            try {
                String[] p = line.split("\t", -1);
                if (p[0].equals("S")) {
                    int id = Integer.parseInt(p[1]);
                    students.add(new Student(id, unescape(p[2]), unescape(p[3]), unescape(p[4])));
                    nextId = Math.max(nextId, id + 1);
                } else if (p[0].equals("A")) {
                    attendance.computeIfAbsent(Integer.parseInt(p[1]), k -> new TreeMap<>()).put(p[2], p[3]);
                }
            } catch (RuntimeException e) {
                System.err.println("Skipping unreadable line " + lineNo + " in " + file);
            }
        }
    }

    private void save() {
        StringBuilder sb = new StringBuilder();
        for (Student s : students) {
            sb.append("S\t").append(s.id()).append('\t')
                    .append(escape(s.rollNo())).append('\t')
                    .append(escape(s.name())).append('\t')
                    .append(escape(s.className())).append('\n');
        }
        for (Map.Entry<Integer, Map<String, String>> e : attendance.entrySet()) {
            for (Map.Entry<String, String> d : e.getValue().entrySet()) {
                sb.append("A\t").append(e.getKey()).append('\t')
                        .append(d.getKey()).append('\t').append(d.getValue()).append('\n');
            }
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write data file " + file, e);
        }
    }

    // ---------- helpers ----------

    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("\\p{Cntrl}", " ").strip();
    }

    private static boolean isNumeric(String s) {
        return !s.isEmpty() && s.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    private static String stripZeros(String s) {
        int i = 0;
        while (i < s.length() - 1 && s.charAt(i) == '0') {
            i++;
        }
        return s.substring(i);
    }

    /** Total order: numeric roll numbers first (2 before 10), then text. */
    static int compareRoll(String a, String b) {
        boolean na = isNumeric(a);
        boolean nb = isNumeric(b);
        if (na != nb) {
            return na ? -1 : 1;
        }
        if (na) {
            String x = stripZeros(a);
            String y = stripZeros(b);
            if (x.length() != y.length()) {
                return Integer.compare(x.length(), y.length());
            }
            int c = x.compareTo(y);
            if (c != 0) {
                return c;
            }
        }
        return a.compareTo(b);
    }

    static String escape(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\' -> b.append("\\\\");
                case '\t' -> b.append("\\t");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                default -> b.append(c);
            }
        }
        return b.toString();
    }

    static String unescape(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case 't' -> b.append('\t');
                    case 'n' -> b.append('\n');
                    case 'r' -> b.append('\r');
                    default -> b.append(n);
                }
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }
}
