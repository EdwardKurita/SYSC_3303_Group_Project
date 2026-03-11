import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive JUnit 5 test cases for DroneData class - Iteration 2
 * Tests drone state tracking, water management, and mission data
 */
class DroneDataTest {

    private DroneData droneData;
    private final int DRONE_ID = 1;

    @BeforeEach
    void setUp() {
        droneData = new DroneData(DRONE_ID);
    }

    @Test
    @DisplayName("Constructor should initialize drone correctly")
    void testConstructor() {
        assertNotNull(droneData, "DroneData should not be null");
        assertEquals(DRONE_ID, droneData.getDroneId(), "Drone ID should be set correctly");
        assertEquals(DroneState.IDLE, droneData.getState(), "Initial state should be IDLE");
        assertEquals(15.0, droneData.getCurrentWater(), 0.1, "Initial water should be 15.0L");
        assertEquals(0, droneData.getCurrentZone(), "Initial zone should be 0 (base)");
        assertNull(droneData.getCurrentMission(), "Initial mission should be null");
        assertTrue(droneData.isAvailable(), "Drone should be available initially");
    }

    @Test
    @DisplayName("Setters and getters should work correctly")
    void testSettersAndGetters() {
        // Test state setter
        droneData.setState(DroneState.EN_ROUTE);
        assertEquals(DroneState.EN_ROUTE, droneData.getState(), "State should be EN_ROUTE");

        // Test water setter
        droneData.setCurrentWater(10.5);
        assertEquals(10.5, droneData.getCurrentWater(), 0.1, "Water should be 10.5L");

        // Test zone setter
        droneData.setCurrentZone(3);
        assertEquals(3, droneData.getCurrentZone(), "Zone should be 3");

        // Test mission setter
        FireEvent mission = new FireEvent("10:00:00", 1, "FIRE_DETECTED", "High");
        droneData.setCurrentMission(mission);
        assertEquals(mission, droneData.getCurrentMission(), "Mission should be set correctly");
    }

    @Test
    @DisplayName("isAvailable should return correct state")
    void testIsAvailable() {
        assertTrue(droneData.isAvailable(), "Should be available in IDLE state");

        droneData.setState(DroneState.EN_ROUTE);
        assertFalse(droneData.isAvailable(), "Should not be available in EN_ROUTE");

        droneData.setState(DroneState.ARRIVED);
        assertFalse(droneData.isAvailable(), "Should not be available in ARRIVED");

        droneData.setState(DroneState.DROPPING_AGENT);
        assertFalse(droneData.isAvailable(), "Should not be available in DROPPING_AGENT");

        droneData.setState(DroneState.COMPLETED);
        assertFalse(droneData.isAvailable(), "Should not be available in COMPLETED");

        droneData.setState(DroneState.RETURNING);
        assertFalse(droneData.isAvailable(), "Should not be available in RETURNING");

        droneData.setState(DroneState.IDLE);
        assertTrue(droneData.isAvailable(), "Should be available again in IDLE");
    }

    @Test
    @DisplayName("hasEnoughWater should check water availability correctly")
    void testHasEnoughWater() {
        // Initial water is 15L
        assertTrue(droneData.hasEnoughWater(10.0), "Should have enough for 10L");
        assertTrue(droneData.hasEnoughWater(15.0), "Should have enough for 15L");
        assertFalse(droneData.hasEnoughWater(20.0), "Should NOT have enough for 20L");

        // After using some water
        droneData.useWater(5.0);
        assertTrue(droneData.hasEnoughWater(10.0), "Should have enough for 10L (10L left)");
        assertFalse(droneData.hasEnoughWater(15.0), "Should NOT have enough for 15L");

        // Edge cases
        droneData.setCurrentWater(0.0);
        assertFalse(droneData.hasEnoughWater(0.1), "Should not have enough for any positive amount");
        assertTrue(droneData.hasEnoughWater(0.0), "Should have enough for 0L");
    }

    @Test
    @DisplayName("useWater should decrease water correctly")
    void testUseWater() {
        // Normal usage
        droneData.useWater(5.0);
        assertEquals(10.0, droneData.getCurrentWater(), 0.1, "Should have 10L left after using 5L");

        droneData.useWater(10.0);
        assertEquals(0.0, droneData.getCurrentWater(), 0.1, "Should have 0L left after using 10L");

        // Should not go below zero
        droneData.useWater(5.0);
        assertEquals(0.0, droneData.getCurrentWater(), 0.1, "Should not go below 0L");

        // Test with different values
        droneData.setCurrentWater(15.0);
        droneData.useWater(7.5);
        assertEquals(7.5, droneData.getCurrentWater(), 0.1, "Should have 7.5L left");
    }

    @Test
    @DisplayName("refill should reset water to full capacity")
    void testRefill() {
        // Use some water
        droneData.useWater(10.0);
        assertEquals(5.0, droneData.getCurrentWater(), 0.1, "Should have 5L left");

        // Refill
        droneData.refill();
        assertEquals(15.0, droneData.getCurrentWater(), 0.1, "Should be refilled to 15L");

        // Refill when already full
        droneData.refill();
        assertEquals(15.0, droneData.getCurrentWater(), 0.1, "Should stay at 15L");
    }

    @Test
    @DisplayName("returnToBase should reset drone to initial state")
    void testReturnToBase() {
        // Set various states
        droneData.setState(DroneState.EN_ROUTE);
        droneData.setCurrentZone(3);
        droneData.useWater(8.0);
        FireEvent mission = new FireEvent("10:00:00", 1, "FIRE_DETECTED", "High");
        droneData.setCurrentMission(mission);

        // Verify non-default state
        assertEquals(DroneState.EN_ROUTE, droneData.getState());
        assertEquals(3, droneData.getCurrentZone());
        assertEquals(7.0, droneData.getCurrentWater(), 0.1);
        assertNotNull(droneData.getCurrentMission());

        // Return to base
        droneData.returnToBase();

        // Verify reset state
        assertEquals(DroneState.IDLE, droneData.getState(), "State should reset to IDLE");
        assertEquals(0, droneData.getCurrentZone(), "Zone should reset to 0");
        assertEquals(15.0, droneData.getCurrentWater(), 0.1, "Water should refill to 15L");
        assertNull(droneData.getCurrentMission(), "Mission should be cleared");
        assertTrue(droneData.isAvailable(), "Drone should be available");
    }

    /*
    @Test
    @DisplayName("updateFromResponse should handle EN_ROUTE status")
    void testUpdateFromEnRouteResponse() {
        DroneResponse response = new DroneResponse(3, "EN_ROUTE", "Traveling", "10:00:00", 0.0);

        droneData.updateFromResponse(response);

        assertEquals(DroneState.EN_ROUTE, droneData.getState(), "State should be EN_ROUTE");
        assertEquals(3, droneData.getCurrentZone(), "Zone should be updated to 3");
        assertEquals(15.0, droneData.getCurrentWater(), 0.1, "Water should remain unchanged");
    }

    @Test
    @DisplayName("updateFromResponse should handle ARRIVED status")
    void testUpdateFromArrivedResponse() {
        DroneResponse response = new DroneResponse(2, "ARRIVED", "Arrived", "10:00:00", 0.0);

        droneData.updateFromResponse(response);

        assertEquals(DroneState.ARRIVED, droneData.getState(), "State should be ARRIVED");
        // Zone might not update for arrived
    }

    @Test
    @DisplayName("updateFromResponse should handle EXTINGUISHING status")
    void testUpdateFromExtinguishingResponse() {
        DroneResponse response = new DroneResponse(1, "EXTINGUISHING", "Dropping", "10:00:00", 0.0);

        droneData.updateFromResponse(response);

        assertEquals(DroneState.DROPPING_AGENT, droneData.getState(), "State should be DROPPING_AGENT");
    }

    @Test
    @DisplayName("updateFromResponse should handle COMPLETED status with water usage")
    void testUpdateFromCompletedResponse() {
        droneData.setCurrentWater(15.0);
        DroneResponse response = new DroneResponse(1, "COMPLETED", "Done", "10:00:00", 10.0);

        droneData.updateFromResponse(response);

        assertEquals(DroneState.COMPLETED, droneData.getState(), "State should be COMPLETED");
        assertEquals(5.0, droneData.getCurrentWater(), 0.1, "Water should decrease by 10L");
    }

    @Test
    @DisplayName("updateFromResponse should handle PARTIAL status with water usage")
    void testUpdateFromPartialResponse() {
        droneData.setCurrentWater(15.0);
        DroneResponse response = new DroneResponse(1, "PARTIAL", "Partial", "10:00:00", 8.0);

        droneData.updateFromResponse(response);

        assertEquals(DroneState.COMPLETED, droneData.getState(), "State should be COMPLETED");
        assertEquals(7.0, droneData.getCurrentWater(), 0.1, "Water should decrease by 8L");
    }

    @Test
    @DisplayName("updateFromResponse should handle RETURNING status")
    void testUpdateFromReturningResponse() {
        DroneResponse response = new DroneResponse(1, "RETURNING", "Returning", "10:00:00", 0.0);

        droneData.updateFromResponse(response);

        assertEquals(DroneState.RETURNING, droneData.getState(), "State should be RETURNING");
    }

    @Test
    @DisplayName("updateFromResponse should handle RETURNED status and reset drone")
    void testUpdateFromReturnedResponse() {
        // First set some state
        droneData.setState(DroneState.RETURNING);
        droneData.setCurrentZone(2);
        droneData.setCurrentWater(5.0);

        DroneResponse response = new DroneResponse(1, "RETURNED", "Returned", "10:00:00", 15.0);

        droneData.updateFromResponse(response);

        assertEquals(DroneState.IDLE, droneData.getState(), "State should reset to IDLE");
        assertEquals(0, droneData.getCurrentZone(), "Zone should reset to 0");
        assertEquals(15.0, droneData.getCurrentWater(), 0.1, "Water should refill to 15L");
    }

    @Test
    @DisplayName("updateFromResponse should handle ERROR status gracefully")
    void testUpdateFromErrorResponse() {
        DroneData droneData = new DroneData(1);
        DroneState originalState = droneData.getState();
        double originalWater = droneData.getCurrentWater();

        DroneResponse response = new DroneResponse(1, "ERROR", "Error", "10:00:00", 0.0);

        // Not throw exception
        assertDoesNotThrow(() -> {
            droneData.updateFromResponse(response);
        }, "ERROR response should not throw exception");
    }

    @Test
    @DisplayName("updateFromResponse should handle unknown status gracefully")
    void testUpdateFromUnknownStatus() {
        DroneState originalState = droneData.getState();

        DroneResponse response = new DroneResponse(1, "UNKNOWN", "Unknown", "10:00:00", 0.0);

        assertDoesNotThrow(() -> {
            droneData.updateFromResponse(response);
        }, "Unknown status should not throw exception");
    }

    @Test
    @DisplayName("toString should format drone data correctly")
    void testToString() {
        String defaultString = droneData.toString();
        assertTrue(defaultString.contains("Drone 1"), "Should contain drone ID");
        assertTrue(defaultString.contains("IDLE"), "Should contain state");
        assertTrue(defaultString.contains("15.0L"), "Should contain water amount");
        assertTrue(defaultString.contains("Zone: 0"), "Should contain zone");

        // Test with custom values
        droneData.setState(DroneState.EN_ROUTE);
        droneData.setCurrentZone(5);
        droneData.setCurrentWater(7.5);

        String customString = droneData.toString();
        assertTrue(customString.contains("EN_ROUTE"), "Should show updated state");
        assertTrue(customString.contains("7.5L"), "Should show updated water");
        assertTrue(customString.contains("Zone: 5"), "Should show updated zone");
    }

    @Test
    @DisplayName("Multiple status updates should work in sequence")
    void testMultipleStatusUpdates() {
        // Simulate a full mission sequence
        DroneResponse[] responses = {
                new DroneResponse(1, "EN_ROUTE", "", "10:00:00", 0.0),
                new DroneResponse(1, "ARRIVED", "", "10:01:00", 0.0),
                new DroneResponse(1, "EXTINGUISHING", "", "10:02:00", 0.0),
                new DroneResponse(1, "COMPLETED", "", "10:03:00", 8.0),
                new DroneResponse(1, "RETURNING", "", "10:04:00", 0.0),
                new DroneResponse(1, "RETURNED", "", "10:05:00", 15.0)
        };

        // Process each response
        for (DroneResponse response : responses) {
            droneData.updateFromResponse(response);
        }

        // Final state should be IDLE with full water
        assertEquals(DroneState.IDLE, droneData.getState(), "Final state should be IDLE");
        assertEquals(15.0, droneData.getCurrentWater(), 0.1, "Final water should be 15L");
        assertEquals(0, droneData.getCurrentZone(), "Final zone should be 0");
    }

    @Test
    @DisplayName("Should handle multiple water usage operations")
    void testMultipleWaterOperations() {
        // Start with 15L

        // Use 5L
        droneData.useWater(5.0);
        assertEquals(10.0, droneData.getCurrentWater(), 0.1);

        // Check hasEnoughWater
        assertTrue(droneData.hasEnoughWater(10.0));
        assertFalse(droneData.hasEnoughWater(10.1));

        // Use another 5L
        droneData.useWater(5.0);
        assertEquals(5.0, droneData.getCurrentWater(), 0.1);

        // Refill
        droneData.refill();
        assertEquals(15.0, droneData.getCurrentWater(), 0.1);

        // Use more than available (it should be cap at 0)
        droneData.useWater(20.0);
        assertEquals(0.0, droneData.getCurrentWater(), 0.1);
    }

    @Test
    @DisplayName("Mission tracking should work correctly")
    void testMissionTracking() {
        assertNull(droneData.getCurrentMission(), "Initial mission should be null");

        FireEvent mission1 = new FireEvent("10:00:00", 2, "FIRE_DETECTED", "Moderate");
        droneData.setCurrentMission(mission1);

        assertEquals(mission1, droneData.getCurrentMission(), "Mission should be set");
        assertEquals(2, droneData.getCurrentMission().getZoneId(), "Mission zone should be 2");
        assertEquals("Moderate", droneData.getCurrentMission().getSeverity(), "Mission severity should be Moderate");

        FireEvent mission2 = new FireEvent("11:00:00", 3, "FIRE_DETECTED", "High");
        droneData.setCurrentMission(mission2);

        assertEquals(mission2, droneData.getCurrentMission(), "Mission should be updated");
        assertEquals(3, droneData.getCurrentMission().getZoneId(), "New mission zone should be 3");

        droneData.setCurrentMission(null);
        assertNull(droneData.getCurrentMission(), "Mission should be cleared");
    }
     */
}
