package attendance;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Used by the Docker HEALTHCHECK: exits 0 when /health answers 200, otherwise 1. */
public final class HealthCheck {

    private HealthCheck() {}

    public static void main(String[] args) {
        String port = System.getenv().getOrDefault("PORT", "8080");
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            System.exit(status == 200 ? 0 : 1);
        } catch (Exception e) {
            System.exit(1);
        }
    }
}
