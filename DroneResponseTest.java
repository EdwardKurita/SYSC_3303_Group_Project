import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 test cases for DroneResponse class
 */
class DroneResponseTest {

    @Test
    @DisplayName("Constructor should initialize all fields correctly")
    void testConstructor() {
        DroneResponse response = new DroneResponse(5, "ARRIVED", "Reached destination", "10:30:45", 25.5);

        assertEquals(5, response.getZoneId());
        assertEquals("ARRIVED", response.getStatus());
        assertEquals("Reached destination", response.getMessage());
        assertEquals("10:30:45", response.getTimestamp());
        assertEquals(25.5, response.getWaterUsed());
    }

    @Test
    @DisplayName("All getters should return correct values")
    void testGetters() {
        DroneResponse response = new DroneResponse(3, "EN_ROUTE", "Traveling to Zone 3", "14:03:15", 0.0);

        assertEquals(3, response.getZoneId());
        assertEquals("EN_ROUTE", response.getStatus());
        assertEquals("Traveling to Zone 3", response.getMessage());
        assertEquals("14:03:15", response.getTimestamp());
        assertEquals(0.0, response.getWaterUsed());
    }

    @Test
    @DisplayName("toString should format output correctly")
    void testToString() {
        DroneResponse response = new DroneResponse(3, "EN_ROUTE", "Traveling to Zone 3", "14:03:15", 0.0);
        String expected = "[14:03:15] Zone 3: EN_ROUTE - Traveling to Zone 3 (Water: 0.0L)";
        assertEquals(expected, response.toString());
    }

    @Test
    @DisplayName("toString should format water with one decimal place")
    void testToStringWithWater() {
        DroneResponse response = new DroneResponse(5, "EXTINGUISHING", "Spraying water", "15:30:22", 42.7);
        String expected = "[15:30:22] Zone 5: EXTINGUISHING - Spraying water (Water: 42.7L)";
        assertEquals(expected, response.toString());
    }

    @Test
    @DisplayName("Should handle all status types")
    void testAllStatuses() {
        DroneResponse enRoute = new DroneResponse(1, "EN_ROUTE", "Moving", "10:00:00", 0.0);
        DroneResponse arrived = new DroneResponse(2, "ARRIVED", "Reached", "10:05:00", 0.0);
        DroneResponse extinguishing = new DroneResponse(3, "EXTINGUISHING", "Fighting", "10:10:00", 15.5);
        DroneResponse completed = new DroneResponse(4, "COMPLETED", "Done", "10:20:00", 50.0);

        assertEquals("EN_ROUTE", enRoute.getStatus());
        assertEquals("ARRIVED", arrived.getStatus());
        assertEquals("EXTINGUISHING", extinguishing.getStatus());
        assertEquals("COMPLETED", completed.getStatus());
    }
}