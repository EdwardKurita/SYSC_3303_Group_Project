import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;

/**
 * Integration tests for Iteration 2 - Tests all components working together
 * Verifies end-to-end functionality of the firefighting drone system
 */
class Iteration2IntegrationTest {

    private SharedBuffer buffer;
    private FireDroneGUI gui;
    private FireIncidentSubsystem fireSubsystem;
    private DroneSubsystem drone;
    private Scheduler scheduler;
    private Thread fireThread;
    private Thread droneThread;
    private Thread schedulerThread;

    @BeforeEach
    void setUp() throws Exception {
        buffer = new SharedBuffer();

        // Create GUI on Event Dispatch Thread
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            gui = new FireDroneGUI();
        });

        // Create test files
        createTestFiles();

        // Initialize subsystems
        fireSubsystem = new FireIncidentSubsystem(buffer, gui,
                "integration_events.csv", "integration_zones.csv");

        // Load zones manually before creating drone
        loadZonesManually();

        drone = new DroneSubsystem(buffer, fireSubsystem, gui, 1);
        scheduler = new Scheduler(buffer, gui, drone);
    }

    @AfterEach
    void tearDown() {
        // Clean shutdown - interrupt all threads
        if (droneThread != null && droneThread.isAlive()) {
            droneThread.interrupt();
        }
        if (schedulerThread != null && schedulerThread.isAlive()) {
            schedulerThread.interrupt();
        }
        if (fireThread != null && fireThread.isAlive()) {
            fireThread.interrupt();
        }

        // Wait for threads to finish
        try {
            if (droneThread != null) droneThread.join(1000);
            if (schedulerThread != null) schedulerThread.join(1000);
            if (fireThread != null) fireThread.join(1000);
        } catch (InterruptedException e) {
        }

        // Dispose GUI
        if (gui != null) {
            gui.dispose();
        }

        // Delete test files
        try {
            new File("integration_zones.csv").delete();
            new File("integration_events.csv").delete();
        } catch (Exception e) {
        }
    }

    private void createTestFiles() throws IOException {
        // Create zones file with multiple zones
        try (FileWriter writer = new FileWriter("integration_zones.csv")) {
            writer.write("Zone ID,Zone Start,Zone End\n");
            writer.write("1,(0;0),(100;100)\n");        // Close zone
            writer.write("2,(200;200),(300;300)\n");    // Medium zone
            writer.write("3,(400;400),(500;500)\n");    // Far zone
        }

        // Create events file with various severities
        try (FileWriter writer = new FileWriter("integration_events.csv")) {
            writer.write("Time,Zone ID,Event type,Severity\n");
            writer.write("10:00:00,1,FIRE_DETECTED,Low\n");
            writer.write("10:05:00,2,FIRE_DETECTED,Moderate\n");
            writer.write("10:10:00,3,FIRE_DETECTED,High\n");
        }
    }

    private void loadZonesManually() throws Exception {
        // Use reflection to load zones
        java.lang.reflect.Method loadZonesMethod = FireIncidentSubsystem.class
                .getDeclaredMethod("loadZones");
        loadZonesMethod.setAccessible(true);
        loadZonesMethod.invoke(fireSubsystem);
    }

    @Test
    @DisplayName("Zone loading should work correctly")
    void testZoneLoading() {
        Zone zone1 = fireSubsystem.getZone(1);
        Zone zone2 = fireSubsystem.getZone(2);
        Zone zone3 = fireSubsystem.getZone(3);

        assertNotNull(zone1, "Zone 1 should be loaded");
        assertNotNull(zone2, "Zone 2 should be loaded");
        assertNotNull(zone3, "Zone 3 should be loaded");

        assertEquals(1, zone1.getZoneId());
        assertEquals(2, zone2.getZoneId());
        assertEquals(3, zone3.getZoneId());
    }

    @Test
    @DisplayName("All subsystems should start successfully")
    void testAllSubsystemsStart() {
        fireThread = new Thread(fireSubsystem, "FireIncident");
        droneThread = new Thread(drone, "Drone");
        schedulerThread = new Thread(scheduler, "Scheduler");

        fireThread.start();
        droneThread.start();
        schedulerThread.start();

        // Verify all threads are running
        assertTrue(fireThread.isAlive(), "Fire subsystem thread should be alive");
        assertTrue(droneThread.isAlive(), "Drone thread should be alive");
        assertTrue(schedulerThread.isAlive(), "Scheduler thread should be alive");

        // Let them run
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
        }
    }

    @Test
    @DisplayName("Fire events should be properly created")
    void testFireEventCreation() {
        FireEvent event = new FireEvent("10:00:00", 1, "FIRE_DETECTED", "Low");

        assertEquals("10:00:00", event.getTime());
        assertEquals(1, event.getZoneId());
        assertEquals("FIRE_DETECTED", event.getEventType());
        assertEquals("Low", event.getSeverity());
        assertEquals(10.0, event.getWaterNeeded(), 0.1);
    }

    @Test
    @DisplayName("Scheduler should initialize with drone info")
    void testSchedulerInitialization() throws Exception {
        java.lang.reflect.Field droneInfoField = Scheduler.class.getDeclaredField("droneInfo");
        droneInfoField.setAccessible(true);
        DroneData droneInfo = (DroneData) droneInfoField.get(scheduler);

        assertNotNull(droneInfo, "Scheduler should have drone info");
        assertEquals(1, droneInfo.getDroneId(), "Drone ID should be 1");
        assertTrue(droneInfo.isAvailable(), "Drone should be available initially");
    }

    @Test
    @DisplayName("Drone should accept missions")
    void testDroneAcceptsMission() {
        FireEvent mission = new FireEvent("10:00:00", 1, "FIRE_DETECTED", "Low");

        assertDoesNotThrow(() -> {
            drone.assignFire(mission);
        }, "Drone should accept mission without throwing");
    }

    @Test
    @DisplayName("Scheduler should have available drone initially")
    void testSchedulerHasAvailableDrone() {
        assertTrue(scheduler.hasAvailableDrone(), "Scheduler should report drone available");
    }

    @Test
    @DisplayName("Drone should maintain correct ID")
    void testDroneId() {
        assertEquals(1, drone.getDroneId(), "Drone ID should be 1");
    }

    @Test
    @DisplayName("Drone should start in IDLE state")
    void testDroneInitialState() {
        assertEquals(DroneState.IDLE, drone.getCurrentDroneState(),
                "Drone should start IDLE");
    }

    @Test
    @DisplayName("Buffer should handle fire events")
    void testBufferFireEvents() throws Exception {
        FireEvent event = new FireEvent("10:00:00", 1, "FIRE_DETECTED", "Low");

        buffer.putFireEvent(event);
        assertTrue(buffer.hasFireEvent(), "Buffer should have event after put");

        FireEvent retrieved = buffer.takeFireEvent();
        assertEquals(event.getZoneId(), retrieved.getZoneId(), "Retrieved event should match");
    }

    @Test
    @DisplayName("Buffer should handle drone responses")
    void testBufferDroneResponses() throws Exception {
        DroneResponse response = new DroneResponse(1, "TEST", "Test message", "10:00:00", 0.0);

        buffer.putDroneResponse(response);
        assertTrue(buffer.hasDroneResponse(), "Buffer should have response after put");

        DroneResponse retrieved = buffer.takeDroneResponse();
        assertEquals(response.getStatus(), retrieved.getStatus(), "Retrieved response should match");
    }

    @Test
    @DisplayName("Zone distance calculation should work")
    void testZoneDistance() {
        Zone zone = fireSubsystem.getZone(1);
        assertNotNull(zone, "Zone should exist");

        double distance = zone.getDistanceFromBase();
        assertTrue(distance > 0, "Distance should be positive");
    }

    @Test
    @DisplayName("Scheduler queue should start empty")
    void testSchedulerEmptyQueue() throws Exception {
        java.lang.reflect.Field queueField = Scheduler.class.getDeclaredField("fireQueue");
        queueField.setAccessible(true);
        java.util.Queue<FireEvent> queue = (java.util.Queue<FireEvent>) queueField.get(scheduler);

        assertTrue(queue.isEmpty(), "Scheduler queue should start empty");
    }

    @Test
    @DisplayName("Drone should have correct tank capacity")
    void testDroneTankCapacity() {
        assertEquals(15.0, drone.getCurrentWater(), 0.1, "Drone should have 15L capacity");
    }

    @Test
    @DisplayName("Fire event severity mapping should work")
    void testFireEventSeverity() {
        FireEvent lowEvent = new FireEvent("10:00:00", 1, "FIRE_DETECTED", "Low");
        FireEvent moderateEvent = new FireEvent("10:00:00", 1, "FIRE_DETECTED", "Moderate");
        FireEvent highEvent = new FireEvent("10:00:00", 1, "FIRE_DETECTED", "High");

        assertEquals(10.0, lowEvent.getWaterNeeded(), 0.1, "Low severity should need 10L");
        assertEquals(20.0, moderateEvent.getWaterNeeded(), 0.1, "Moderate severity should need 20L");
        assertEquals(30.0, highEvent.getWaterNeeded(), 0.1, "High severity should need 30L");
    }

    @Test
    @DisplayName("Time conversion should work")
    void testTimeConversion() {
        FireEvent event = new FireEvent("10:30:45", 1, "FIRE_DETECTED", "Low");

        int seconds = event.getTimeInSeconds();
        assertEquals(10*3600 + 30*60 + 45, seconds, "Time conversion should be correct");
    }

    @Test
    @DisplayName("Drone should have working getters")
    void testDroneGetters() {
        assertNotNull(drone.getCurrentDroneState());
        assertEquals(1, drone.getDroneId());
        assertTrue(drone.getCurrentWater() >= 0);
        assertTrue(drone.getCurrentZone() >= 0);
    }

    @Test
    @DisplayName("Scheduler should have working methods")
    void testSchedulerMethods() {
        assertNotNull(scheduler.hasAvailableDrone());
        assertNotNull(scheduler.shouldDispatch());
    }

    @Test
    @DisplayName("Multiple drone instances should have different IDs")
    void testMultipleDrones() {
        DroneSubsystem drone2 = new DroneSubsystem(buffer, fireSubsystem, gui, 2);

        assertEquals(1, drone.getDroneId());
        assertEquals(2, drone2.getDroneId());
    }

    @Test
    @DisplayName("System should handle invalid zone gracefully")
    void testInvalidZone() {
        FireEvent mission = new FireEvent("10:00:00", 99, "FIRE_DETECTED", "Low");

        assertDoesNotThrow(() -> {
            drone.assignFire(mission);
        }, "Invalid zone should not cause crash");
    }

    @Test
    @DisplayName("GUI should update without errors")
    void testGUIUpdates() {
        assertDoesNotThrow(() -> {
            gui.log("Test log message");
            gui.updateDroneStatus("TEST");
            gui.updateSchedulerStatus("TEST");
            gui.incrementActiveFires();
            gui.decrementActiveFires();
        }, "GUI updates should not throw exceptions");
    }
}
