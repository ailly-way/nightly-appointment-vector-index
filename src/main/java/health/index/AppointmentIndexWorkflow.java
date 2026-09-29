package health.index;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AppointmentIndexWorkflow {
    private final InfraiClient infrai;

    public AppointmentIndexWorkflow(InfraiClient infrai) { this.infrai = infrai; }

    public record Appointment(String id, String status, String startsAt, String location,
                              String preparation, String contactChannel, String clinicalNote) {}

    public record OperationalDocument(String id, String text, Map<String, Object> metadata) {}

    public static OperationalDocument patientSafeDocument(Appointment appointment) {
        boolean attend = appointment.status().equals("scheduled") || appointment.status().equals("rescheduled");
        String action = attend
                ? "Appointment " + appointment.status() + " for " + appointment.startsAt() + " at " + appointment.location()
                  + ". Preparation: " + appointment.preparation() + ". Questions: " + appointment.contactChannel() + "."
                : "Appointment " + appointment.status() + ". Contact " + appointment.contactChannel() + " to arrange another time.";
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("appointment_id", appointment.id());
        metadata.put("status", appointment.status());
        metadata.put("notification_kind", "operational");
        return new OperationalDocument(appointment.id(), action, Map.copyOf(metadata));
    }

    public Map<String, Object> reindex(String operationsPage, List<Appointment> appointments)
            throws IOException, InterruptedException {
        String page = infrai.scrape(operationsPage);
        int indexed = 0;
        for (Appointment appointment : appointments) {
            OperationalDocument document = patientSafeDocument(appointment);
            String input = document.text() + "\nCurrent clinic guidance:\n" + page;
            List<Double> vector = infrai.embed(input);
            infrai.upsert(document.id(), vector, document.metadata());
            indexed++;
        }
        return Map.of("indexed", indexed, "source", operationsPage, "state", "fresh");
    }
}

