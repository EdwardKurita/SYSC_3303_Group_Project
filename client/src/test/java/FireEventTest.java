import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * JUnit 5 test cases for FireEvent class
 */
class FireEventTest {
    
    @Test
    @DisplayName("Constructor should initialize all fields correctly")
    void testConstructor() {
        FireEvent event = new FireEvent("14:30:45", 5, "FIRE_DETECTED", "High");

        assertEquals("14:30:45", event.getTime());
        assertEquals(5, event.getZoneId());
        assertEquals("FIRE_DETECTED", event.getEventType());
        assertEquals("High", event.getSeverity());
    }

    @Test
    @DisplayName("GetTimeInSeconds should convert time correctly")
    void testGetTimeInSeconds() {
        FireEvent event1 = new FireEvent("01:30:45", 1, "FIRE_DETECTED", "High");
        // 1 hour = 3600, 30 min = 1800, 45 sec = 45 Total: 5445
        assertEquals(5445, event1.getTimeInSeconds());

        FireEvent event2 = new FireEvent("00:05:30", 2, "FIRE_DETECTED", "Low");
        // 5 min = 300, 30 sec = 30 Total: 330
        assertEquals(330, event2.getTimeInSeconds());

        FireEvent event3 = new FireEvent("12:00:00", 3, "FIRE_DETECTED", "Moderate");
        // 12 hours = 43200
        assertEquals(43200, event3.getTimeInSeconds());
    }

    @Test
    @DisplayName("GetWaterNeeded should return correct amount for High severity")
    void testGetWaterNeededHigh() {
        FireEvent event = new FireEvent("10:00:00", 1, "FIRE_DETECTED", "High");
        assertEquals(30.0, event.getWaterNeeded());

        // Test case insensitivity
        FireEvent event2 = new FireEvent("10:00:00", 1, "FIRE_DETECTED", "HIGH");
        assertEquals(30.0, event2.getWaterNeeded());
    }

    @Test
    @DisplayName("GetWaterNeeded should return correct amount for Moderate severity")
    void testGetWaterNeededModerate() {
        FireEvent event = new FireEvent("10:00:00", 2, "FIRE_DETECTED", "Moderate");
        assertEquals(20.0, event.getWaterNeeded());

        FireEvent event2 = new FireEvent("10:00:00", 2, "FIRE_DETECTED", "MODERATE");
        assertEquals(20.0, event2.getWaterNeeded());
    }

    @Test
    @DisplayName("GetWaterNeeded should return correct amount for Low severity")
    void testGetWaterNeededLow() {
        FireEvent event = new FireEvent("10:00:00", 3, "FIRE_DETECTED", "Low");
        assertEquals(10.0, event.getWaterNeeded());

        FireEvent event2 = new FireEvent("10:00:00", 3, "FIRE_DETECTED", "LOW");
        assertEquals(10.0, event2.getWaterNeeded());
    }

    @Test
    @DisplayName("GetWaterNeeded should return default for unknown severity")
    void testGetWaterNeededDefault() {
        FireEvent event = new FireEvent("10:00:00", 4, "FIRE_DETECTED", "Unknown");
        assertEquals(10.0, event.getWaterNeeded());

        FireEvent event2 = new FireEvent("10:00:00", 4, "FIRE_DETECTED", "");
        assertEquals(10.0, event2.getWaterNeeded());
    }

    @Test
    @DisplayName("ToString should format output correctly")
    void testToString() {
        FireEvent event1 = new FireEvent("14:03:15", 3, "FIRE_DETECTED", "High");
        String expected1 = "[14:03:15] Zone 3: FIRE_DETECTED (High)";
        assertEquals(expected1, event1.toString());

        FireEvent event2 = new FireEvent("08:30:00", 7, "DRONE_REQUEST", "Moderate");
        String expected2 = "[08:30:00] Zone 7: DRONE_REQUEST (Moderate)";
        assertEquals(expected2, event2.toString());
    }

    @Test
    @DisplayName("Should handle different event types")
    void testEventTypes() {
        FireEvent fireDetected = new FireEvent("10:00:00", 1, "FIRE_DETECTED", "High");
        assertEquals("FIRE_DETECTED", fireDetected.getEventType());

        FireEvent droneRequest = new FireEvent("10:00:00", 2, "DRONE_REQUEST", "Low");
        assertEquals("DRONE_REQUEST", droneRequest.getEventType());
    }
}