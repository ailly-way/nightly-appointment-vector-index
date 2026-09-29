package health.index;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class AppointmentIndexService {
    private AppointmentIndexService() {}

    public static void main(String[] args) throws Exception {
        ServiceConfig config = ServiceConfig.fromEnvironment();
        InfraiClient infrai = new InfraiClient(config);
        AppointmentIndexWorkflow workflow = new AppointmentIndexWorkflow(infrai);
        HttpServer server = HttpServer.create(new InetSocketAddress(config.port()), 0);

        server.createContext("/internal/nightly-reindex", exchange -> {
            if (!exchange.getRequestMethod().equals("POST")) { send(exchange, 405, Map.of("error", "POST required")); return; }
            try {
                List<AppointmentIndexWorkflow.Appointment> appointments = List.of(
                        new AppointmentIndexWorkflow.Appointment("appt-1042", "scheduled", "2026-09-20T09:30:00+08:00",
                                "Harbor Clinic, second floor", "Bring photo identification and your current medication list",
                                "the scheduling desk", "Clinical notes stay outside the search document"));
                send(exchange, 200, workflow.reindex("https://clinic.example.org/appointments/prepare", appointments));
            } catch (InfraiClient.InfraiException error) {
                send(exchange, error.status() >= 400 && error.status() < 500 ? error.status() : 502,
                        Map.of("error", error.code(), "message", error.getMessage()));
            } catch (Exception error) {
                send(exchange, 502, Map.of("error", "reindex_failed", "message", error.getMessage()));
            }
        });

        server.createContext("/admin/install-schedule", exchange -> {
            if (!exchange.getRequestMethod().equals("POST")) { send(exchange, 405, Map.of("error", "POST required")); return; }
            try {
                Map<String, Object> collection = infrai.createCollection(1536);
                Map<String, Object> schedule = infrai.createNightlySchedule();
                send(exchange, 201, Map.of("collection", collection, "schedule", schedule));
            }
            catch (InfraiClient.InfraiException error) {
                send(exchange, error.status() >= 400 && error.status() < 500 ? error.status() : 502,
                        Map.of("error", error.code(), "message", error.getMessage()));
            } catch (Exception error) { send(exchange, 502, Map.of("error", "schedule_failed", "message", error.getMessage())); }
        });

        server.start();
        System.out.println("Appointment index service listening on http://localhost:" + config.port());
    }

    private static void send(HttpExchange exchange, int status, Map<String, Object> body) throws IOException {
        byte[] bytes = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
