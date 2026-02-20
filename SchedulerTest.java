import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * Comprehensive JUnit 5 test cases for Scheduler class - Iteration 2
 */
class SchedulerTest {

    private Scheduler scheduler;
    private SharedBuffer buffer;
    private FireDroneGUI gui;
    private DroneSubsystem drone;
    private FireIncidentSubsystem fireSubsystem;

    // For capturing System.out to verify errors
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @BeforeEach
    void setUp() throws Exception {
        buffer = new SharedBuffer();

        // Create GUI on Event Dispatch Thread
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            gui = new FireDroneGUI();
        });

        // Create test zones file
        createTestZoneFile();
        createTestEventsFile();

        // Create minimal fire subsystem with test data
        fireSubsystem = new FireIncidentSubsystem(buffer, gui, "test_events.csv", "test_zones.csv");

        drone = new DroneSubsystem(buffer, fireSubsystem, gui, 1);
        scheduler = new Scheduler(buffer, gui, drone);
    }

    @BeforeEach
    void setUpStreams() {
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
    }

    @AfterEach
    void tearDown() {
        // Stop scheduler by setting running flag to false using reflection
        try {
            java.lang.reflect.Field runningField = Scheduler.class.getDeclaredField("running");
            runningField.setAccessible(true);
            runningField.set(scheduler, false);
        } catch (Exception e) {
        }

        // Dispose GUI
        if (gui != null) {
            gui.dispose();
        }

        // Delete test files
        try {
            new java.io.File("test_zones.csv").delete();
            new java.io.File("test_events.csv").delete();
        } catch (Exception e) {
        }
    }

    private void createTestZoneFile() throws java.io.IOException {
        try (java.io.FileWriter writer = new java.io.FileWriter("test_zones.csv")) {
            writer.write("Zone ID,Zone Start,Zone End\n");
            writer.write("1,(0;0),(100;100)\n");
            writer.write("2,(100;100),(200;200)\n");
            writer.write("3,(200;200),(300;300)\n");
        }
    }

    private void createTestEventsFile() throws java.io.IOException {
        try (java.io.FileWriter writer = new java.io.FileWriter("test_events.csv")) {
            writer.write("Time,Zone ID,Event type,Severity\n");
            writer.write("10:00:00,1,FIRE_DETECTED,Low\n");
        }
    }

    @Test
    @DisplayName("Scheduler constructor should initialize correctly")
    void testConstructor() {
        assertNotNull(scheduler);
        assertNotNull(scheduler.hasAvailableDrone());
    }

    @Test
    @DisplayName("Scheduler should have empty queue initially")
    void testInitialQueue() throws Exception {
        java.lang.reflect.Field fireQueueField = Scheduler.class.getDeclaredField("fireQueue");
        fireQueueField.setAccessible(true);
        java.util.Queue<FireEvent> queue = (java.util.Queue<FireEvent>) fireQueueField.get(scheduler);
        assertTrue(queue.isEmpty(), "Initial queue should be empty");
    }

    @Test
    @DisplayName("shouldDispatch should return false when queue empty")
    void testShouldDispatchEmptyQueue() {
        assertFalse(scheduler.shouldDispatch(), "shouldDispatch should be false with empty queue");
    }

    @Test
    @DisplayName("hasAvailableDrone should return true initially")
    void testHasAvailableDrone() {
        assertTrue(scheduler.hasAvailableDrone(), "Drone should be available initially");
    }

    @Test
    @DisplayName("DroneData constructor should initialize correctly")
    void testDroneDataConstructor() {
        DroneData droneData = new DroneData(1);

        assertEquals(1, droneData.getDroneId(), "Drone ID should be 1");
        assertEquals(DroneState.IDLE, droneData.getState(), "Initial state should be IDLE");
        assertEquals(15.0, droneData.getCurrentWater(), 0.1, "Initial water should be 15.0L");
        assertEquals(0, droneData.getCurrentZone(), "Initial zone should be 0 (base)");
        assertNull(droneData.getCurrentMission(), "Initial mission should be null");
        assertTrue(droneData.isAvailable(), "Drone should be available initially");
    }

    @Test
    @DisplayName("DroneData should track water usage correctly")
    void testDroneDataWaterTracking() {
        DroneData droneData = new DroneData(1);

        // Test hasEnoughWater
        assertTrue(droneData.hasEnoughWater(10.0), "Should have enough for 10L");
        assertTrue(droneData.hasEnoughWater(15.0), "Should have enough for 15L");
        assertFalse(droneData.hasEnoughWater(20.0), "Should NOT have enough for 20L");

        // Test useWater
        droneData.useWater(5.0);
        assertEquals(10.0, droneData.getCurrentWater(), 0.1, "After using 5L, should have 10L left");

        droneData.useWater(10.0);
        assertEquals(0.0, droneData.getCurrentWater(), 0.1, "After using 10L, should have 0L left");

        // Test useWater doesn't go negative
        droneData.useWater(5.0);
        assertEquals(0.0, droneData.getCurrentWater(), 0.1, "Should not go below 0L");

        // Test refill
        droneData.refill();
        assertEquals(15.0, droneData.getCurrentWater(), 0.1, "After refill, should be 15L");
    }

    @Test
    @DisplayName("DroneData should track state correctly")
    void testDroneDataStateTracking() {
        DroneData droneData = new DroneData(1);

        assertTrue(droneData.isAvailable(), "Should be available in IDLE state");
        assertEquals(DroneState.IDLE, droneData.getState(), "Initial state should be IDLE");

        droneData.setState(DroneState.EN_ROUTE);
        assertFalse(droneData.isAvailable(), "Should NOT be available in EN_ROUTE state");
        assertEquals(DroneState.EN_ROUTE, droneData.getState(), "State should be EN_ROUTE");

        droneData.setState(DroneState.ARRIVED);
        assertEquals(DroneState.ARRIVED, droneData.getState(), "State should be ARRIVED");
        assertFalse(droneData.isAvailable(), "Should NOT be available in ARRIVED state");
    }

    @Test
    @DisplayName("DroneData returnToBase should reset to idle state")
    void testDroneDataReturnToBase() {
        DroneData droneData = new DroneData(1);

        // Set non-default values
        droneData.setState(DroneState.EN_ROUTE);
        droneData.setCurrentZone(1);
        droneData.useWater(5.0);
        FireEvent mission = new FireEvent("10:00:00", 1, "FIRE_DETECTED", "High");
        droneData.setCurrentMission(mission);

        // Return to base
        droneData.returnToBase();

        assertEquals(DroneState.IDLE, droneData.getState(), "State should reset to IDLE");
        assertEquals(0, droneData.getCurrentZone(), "Zone should reset to 0");
        assertEquals(15.0, droneData.getCurrentWater(), 0.1, "Water should refill to 15L");
        assertNull(droneData.getCurrentMission(), "Mission should be cleared");
        assertTrue(droneData.isAvailable(), "Drone should be available after return");
    }

    @Test
    @DisplayName("DroneData should update from EN_ROUTE response")
    void testUpdateFromEnRouteResponse() {
        DroneData droneData = new DroneData(1);
        DroneResponse response = new DroneResponse(1, "EN_ROUTE", "Traveling", "10:00:00", 0.0);

        droneData.updateFromResponse(response);

        assertEquals(DroneState.EN_ROUTE, droneData.getState(), "State should be EN_ROUTE");
        assertEquals(1, droneData.getCurrentZone(), "Zone should be set to 1");
        assertEquals(15.0, droneData.getCurrentWater(), 0.1, "Water should remain 15L");
    }

    @Test
    @DisplayName("DroneData should update from ARRIVED response")
    void testUpdateFromArrivedResponse() {
        DroneData droneData = new DroneData(1);
        DroneResponse response = new DroneResponse(1, "ARRIVED", "Arrived", "10:00:00", 0.0);

        droneData.updateFromResponse(response);

        assertEquals(DroneState.ARRIVED, droneData.getState(), "State should be ARRIVED");
    }

    @Test
    @DisplayName("DroneData should update from EXTINGUISHING response")
    void testUpdateFromExtinguishingResponse() {
        DroneData droneData = new DroneData(1);
        DroneResponse response = new DroneResponse(1, "EXTINGUISHING", "Dropping", "10:00:00", 0.0);

        droneData.updateFromResponse(response);

        assertEquals(DroneState.DROPPING_AGENT, droneData.getState(), "State should be DROPPING_AGENT");
    }

    @Test
    @DisplayName("DroneData should update from COMPLETED response")
    void testUpdateFromCompletedResponse() {
        DroneData droneData = new DroneData(1);
        droneData.setCurrentWater(15.0);

        DroneResponse response = new DroneResponse(1, "COMPLETED", "Done", "10:00:00", 10.0);

        droneData.updateFromResponse(response);

        assertEquals(DroneState.COMPLETED, droneData.getState(), "State should be COMPLETED");
        assertEquals(5.0, droneData.getCurrentWater(), 0.1, "Water should be reduced by 10L");
    }

    @Test
    @DisplayName("DroneData should update from PARTIAL response")
    void testUpdateFromPartialResponse() {
        DroneData droneData = new DroneData(1);
        droneData.setCurrentWater(15.0);

        DroneResponse response = new DroneResponse(1, "PARTIAL", "Partial", "10:00:00", 10.0);

        droneData.updateFromResponse(response);

        assertEquals(DroneState.COMPLETED, droneData.getState(), "State should be COMPLETED");
        assertEquals(5.0, droneData.getCurrentWater(), 0.1, "Water should be reduced by 10L");
    }

    @Test
    @DisplayName("DroneData should update from RETURNING response")
    void testUpdateFromReturningResponse() {
        DroneData droneData = new DroneData(1);
        DroneResponse response = new DroneResponse(1, "RETURNING", "Returning", "10:00:00", 0.0);

        droneData.updateFromResponse(response);

        assertEquals(DroneState.RETURNING, droneData.getState(), "State should be RETURNING");
    }

    @Test
    @DisplayName("DroneData should update from RETURNED response")
    void testUpdateFromReturnedResponse() {
        DroneData droneData = new DroneData(1);

        // First set some state
        droneData.setState(DroneState.RETURNING);
        droneData.setCurrentZone(1);
        droneData.setCurrentWater(5.0);

        DroneResponse response = new DroneResponse(1, "RETURNED", "Returned", "10:00:00", 15.0);

        droneData.updateFromResponse(response);

        assertEquals(DroneState.IDLE, droneData.getState(), "State should reset to IDLE");
        assertEquals(0, droneData.getCurrentZone(), "Zone should reset to 0");
        assertEquals(15.0, droneData.getCurrentWater(), 0.1, "Water should refill to 15L");
    }

    @Test
    @DisplayName("DroneData should handle ERROR response without crashing")
    void testUpdateFromErrorResponse() {
        // Clear the output stream before test
        outContent.reset();

        DroneData droneData = new DroneData(1);
        DroneResponse response = new DroneResponse(1, "ERROR", "Error", "10:00:00", 0.0);

        // This should not throw an exception
        assertDoesNotThrow(() -> {
            droneData.updateFromResponse(response);
        }, "ERROR response should not throw exception");

        // Verify the error message was printed
        String output = outContent.toString();
        assertTrue(output.contains("ERROR HAS OCCURRED WITH droneData"),
                "Should print error message when ERROR response received");

        // State should remain unchanged
        assertEquals(DroneState.IDLE, droneData.getState(), "State should remain IDLE");
    }

    @Test
    @DisplayName("DroneData should handle unknown status gracefully")
    void testUpdateFromUnknownStatus() {
        DroneData droneData = new DroneData(1);
        DroneResponse response = new DroneResponse(1, "UNKNOWN", "Unknown", "10:00:00", 0.0);

        // Not throw an exception
        assertDoesNotThrow(() -> {
            droneData.updateFromResponse(response);
        }, "Unknown status should not throw exception");
    }

    @Test
    @DisplayName("DroneData toString should format correctly")
    void testDroneDataToString() {
        DroneData droneData = new DroneData(1);
        String str = droneData.toString();

        assertTrue(str.contains("Drone 1"), "Should contain drone ID");
        assertTrue(str.contains("IDLE"), "Should contain state");
        assertTrue(str.contains("15.0L"), "Should contain water amount");
        assertTrue(str.contains("Zone: 0"), "Should contain zone");

        droneData.setState(DroneState.EN_ROUTE);
        droneData.setCurrentZone(3);
        droneData.setCurrentWater(10.5);

        str = droneData.toString();
        assertTrue(str.contains("EN_ROUTE"), "Should show updated state");
        assertTrue(str.contains("10.5L"), "Should show updated water");
        assertTrue(str.contains("Zone: 3"), "Should show updated zone");
    }

    @Test
    @DisplayName("DroneData should track mission correctly")
    void testDroneDataMission() {
        DroneData droneData = new DroneData(1);
        FireEvent mission = new FireEvent("10:00:00", 1, "FIRE_DETECTED", "High");

        droneData.setCurrentMission(mission);
        assertEquals(mission, droneData.getCurrentMission(), "Mission should be set correctly");

        droneData.setCurrentMission(null);
        assertNull(droneData.getCurrentMission(), "Mission should be cleared");
    }

    @Test
    @DisplayName("DroneData should handle full mission sequence")
    void testFullMissionSequence() {
        DroneData droneData = new DroneData(1);

        // Create response sequence
        DroneResponse[] responses = {
                new DroneResponse(1, "EN_ROUTE", "", "10:00:00", 0.0),
                new DroneResponse(1, "ARRIVED", "", "10:01:00", 0.0),
                new DroneResponse(1, "EXTINGUISHING", "", "10:02:00", 0.0),
                new DroneResponse(1, "COMPLETED", "", "10:03:00", 10.0),
                new DroneResponse(1, "RETURNING", "", "10:04:00", 0.0),
                new DroneResponse(1, "RETURNED", "", "10:05:00", 15.0)
        };

        DroneState[] expectedStates = {
                DroneState.EN_ROUTE,
                DroneState.ARRIVED,
                DroneState.DROPPING_AGENT,
                DroneState.COMPLETED,
                DroneState.RETURNING,
                DroneState.IDLE
        };

        double[] expectedWater = {
                15.0, 15.0, 15.0, 5.0, 5.0, 15.0
        };

        for (int i = 0; i < responses.length; i++) {
            droneData.updateFromResponse(responses[i]);
            assertEquals(expectedStates[i], droneData.getState(),
                    "State mismatch at step " + i);
            assertEquals(expectedWater[i], droneData.getCurrentWater(), 0.1,
                    "Water mismatch at step " + i);
        }
    }

    @Test
    @DisplayName("Scheduler should initialize droneInfo correctly")
    void testSchedulerDroneInfo() throws Exception {
        java.lang.reflect.Field droneInfoField = Scheduler.class.getDeclaredField("droneInfo");
        droneInfoField.setAccessible(true);
        DroneData droneInfo = (DroneData) droneInfoField.get(scheduler);

        assertNotNull(droneInfo, "droneInfo should not be null");
        assertEquals(1, droneInfo.getDroneId(), "Drone ID should be 1");
        assertEquals(DroneState.IDLE, droneInfo.getState(), "Drone should start IDLE");
    }
}
