package health.index;

public final class AppointmentIndexWorkflowTest {
    public static void main(String[] args) {
        var scheduled = new AppointmentIndexWorkflow.Appointment("a-1", "scheduled", "Monday 09:00", "Learning Clinic",
                "Bring the intake form", "scheduling desk", "suspected diagnosis: private");
        var ready = AppointmentIndexWorkflow.patientSafeDocument(scheduled);
        check(ready.text().contains("Bring the intake form"), "preparation belongs in the reminder");
        check(!ready.text().contains("suspected diagnosis"), "clinical notes must stay out of the index");
        check(ready.metadata().get("notification_kind").equals("operational"), "notification must be operational");

        var cancelled = new AppointmentIndexWorkflow.Appointment("a-2", "cancelled", "Tuesday 11:00", "Learning Clinic",
                "Arrive early", "scheduling desk", "private");
        var stopped = AppointmentIndexWorkflow.patientSafeDocument(cancelled);
        check(!stopped.text().contains("Arrive early"), "cancelled appointments must not carry arrival instructions");
        check(stopped.text().contains("arrange another time"), "cancelled appointments need a safe next action");
        System.out.println("PASS: operational notification boundary and cancellation decision");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

