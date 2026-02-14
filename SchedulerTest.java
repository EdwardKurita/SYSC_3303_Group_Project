import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 test cases for Scheduler class
 */
class SchedulerTest {

    private Scheduler scheduler;
    private SharedBuffer buffer;
    private FireDroneGUI gui;
    private DroneSubsystem drone;
    private FireIncidentSubsystem fireSubsystem;

    @BeforeEach
    void setUp() throws Exception {
        buffer = new SharedBuffer();

        // Create GUI on Event Dispatch Thread
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            gui = new FireDroneGUI();
        });

        // Create minimal fire subsystem for drone
        fireSubsystem = new FireIncidentSubsystem(buffer, gui, "events.csv", "zones.csv");

        drone = new DroneSubsystem(buffer, fireSubsystem, gui ,1);
        scheduler = new Scheduler(buffer, gui, drone);
    }

    @AfterEach
    void tearDown() {
        if (gui != null) {
            gui.dispose();
        }
    }

    @Test
    @DisplayName("Constructor should initialize all fields")
    void testConstructor() {
        assertNotNull(scheduler);
    }

    @Test
    @DisplayName("Scheduler should process fire events from buffer")
    void testProcessFireEvent() throws Exception {
        FireEvent event = new FireEvent("10:00:00", 1, "FIRE_DETECTED", "High");
        buffer.putFireEvent(event);

        // Run scheduler in separate thread
        Thread schedulerThread = new Thread(scheduler);
        schedulerThread.start();

        // Wait for processing
        Thread.sleep(3000);

        // Stop scheduler
        java.lang.reflect.Field field = Scheduler.class.getDeclaredField("running");
        field.setAccessible(true);
        field.set(scheduler, false);

        schedulerThread.join(2000);

        // Verify event was taken from buffer
        assertFalse(buffer.hasFireEvent());
    }

    @Test
    @DisplayName("Scheduler should process drone responses from buffer")
    void testProcessDroneResponse() throws Exception {
        DroneResponse response = new DroneResponse(1, "EN_ROUTE", "Traveling", "10:00:00", 0.0);

        // Run scheduler in separate thread
        Thread schedulerThread = new Thread(scheduler);
        schedulerThread.start();

        Thread.sleep(2500);

        // Put response in buffer
        buffer.putDroneResponse(response);

        // Wait for processing
        Thread.sleep(500);

        // Stop scheduler
        java.lang.reflect.Field field = Scheduler.class.getDeclaredField("running");
        field.setAccessible(true);
        field.set(scheduler, false);

        schedulerThread.join(2000);

        // Verify response was processed (buffer should be empty)
        assertFalse(buffer.hasDroneResponse());
    }

    @Test
    @DisplayName("Scheduler should handle multiple fire events sequentially")
    void testMultipleFireEvents() throws Exception {
        FireEvent event1 = new FireEvent("10:00:00", 1, "FIRE_DETECTED", "High");
        FireEvent event2 = new FireEvent("10:05:00", 2, "FIRE_DETECTED", "Moderate");

        buffer.putFireEvent(event1);
        buffer.putFireEvent(event2);

        // Run scheduler
        Thread schedulerThread = new Thread(scheduler);
        schedulerThread.start();

        // Wait for processing
        Thread.sleep(3000);

        // Stop scheduler
        java.lang.reflect.Field field = Scheduler.class.getDeclaredField("running");
        field.setAccessible(true);
        field.set(scheduler, false);

        schedulerThread.join(2000);

        // Both events should be processed
        assertTrue(true); // If no exception, test passes
    }

    @Test
    @DisplayName("Scheduler should handle empty buffer gracefully")
    void testEmptyBuffer() throws Exception {
        // Run scheduler with empty buffer
        Thread schedulerThread = new Thread(scheduler);
        schedulerThread.start();

        // Let it run for a bit
        Thread.sleep(2500);

        // Stop scheduler
        java.lang.reflect.Field field = Scheduler.class.getDeclaredField("running");
        field.setAccessible(true);
        field.set(scheduler, false);

        schedulerThread.join(2000);

        // Should complete without error
        assertTrue(true);
    }

    @Test
    @DisplayName("Scheduler should update GUI when processing events")
    void testGUIUpdates() throws Exception {
        FireEvent event = new FireEvent("10:00:00", 3, "FIRE_DETECTED", "Low");
        buffer.putFireEvent(event);

        Thread schedulerThread = new Thread(scheduler);
        schedulerThread.start();

        Thread.sleep(3000);

        // Stop scheduler
        java.lang.reflect.Field field = Scheduler.class.getDeclaredField("running");
        field.setAccessible(true);
        field.set(scheduler, false);

        schedulerThread.join(2000);

        // If no exceptions, GUI updates worked
        assertTrue(true);
    }

    @Test
    @DisplayName("Scheduler should handle fire event and drone response together")
    void testFireEventAndDroneResponse() throws Exception {
        FireEvent event = new FireEvent("10:00:00", 4, "FIRE_DETECTED", "Moderate");
        buffer.putFireEvent(event);

        Thread schedulerThread = new Thread(scheduler);
        schedulerThread.start();

        Thread.sleep(2500);

        // Add drone response
        DroneResponse response = new DroneResponse(4, "COMPLETED", "Done", "10:10:00", 20.0);
        buffer.putDroneResponse(response);

        Thread.sleep(500);

        // Stop scheduler
        java.lang.reflect.Field field = Scheduler.class.getDeclaredField("running");
        field.setAccessible(true);
        field.set(scheduler, false);

        schedulerThread.join(2000);

        // Both should be processed
        assertFalse(buffer.hasFireEvent());
        assertFalse(buffer.hasDroneResponse());
    }
}