import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 test cases for Zone class
 */
class ZoneTest {

    @Test
    @DisplayName("Constructor should initialize all fields correctly")
    void testConstructor() {
        Zone zone = new Zone(1, 0, 0, 100, 100);

        assertEquals(1, zone.getZoneId());
        assertEquals(0, zone.getStartX());
        assertEquals(0, zone.getStartY());
        assertEquals(100, zone.getEndX());
        assertEquals(100, zone.getEndY());
    }

    @Test
    @DisplayName("GetCenterX and getCenterY should calculate correct center points")
    void testGetCenter() {
        Zone zone1 = new Zone(1, 0, 0, 100, 100);
        assertEquals(50, zone1.getCenterX());
        assertEquals(50, zone1.getCenterY());

        Zone zone2 = new Zone(2, 100, 200, 300, 400);
        assertEquals(200, zone2.getCenterX());
        assertEquals(300, zone2.getCenterY());

        Zone zone3 = new Zone(3, 50, 75, 150, 125);
        assertEquals(100, zone3.getCenterX());
        assertEquals(100, zone3.getCenterY());
    }

    @Test
    @DisplayName("GetDistanceFromBase should calculate correct distance")
    void testGetDistanceFromBase() {
        // Zone at (50, 50) center
        Zone zone1 = new Zone(1, 0, 0, 100, 100);
        double expected1 = Math.sqrt(50 * 50 + 50 * 50); // sqrt(5000) ≈ 70.71
        assertEquals(expected1, zone1.getDistanceFromBase(), 0.01);

        // Zone at (300, 400) center - classic 3-4-5 triangle
        Zone zone2 = new Zone(2, 0, 0, 600, 800);
        double expected2 = Math.sqrt(300 * 300 + 400 * 400); // = 500
        assertEquals(500.0, zone2.getDistanceFromBase(), 0.01);

        // Zone at origin (0, 0) center
        Zone zone3 = new Zone(3, 0, 0, 0, 0);
        assertEquals(0.0, zone3.getDistanceFromBase(), 0.01);
    }

    @Test
    @DisplayName("GetDistanceFromBase should handle negative coordinates")
    void testGetDistanceFromBaseNegativeCoordinates() {
        // Zone with negative start coordinates
        Zone zone = new Zone(4, -100, -100, 100, 100);
        // Center is (0, 0), so distance is 0
        assertEquals(0.0, zone.getDistanceFromBase(), 0.01);

        // Zone entirely in negative quadrant
        Zone zone2 = new Zone(5, -200, -200, -100, -100);
        // Center is (-150, -150)
        double expected = Math.sqrt(150 * 150 + 150 * 150);
        assertEquals(expected, zone2.getDistanceFromBase(), 0.01);
    }

    @Test
    @DisplayName("ToString should format output correctly")
    void testToString() {
        Zone zone1 = new Zone(3, 1000, 200, 1200, 400);
        // Center: (1100, 300), Distance: sqrt(1100^2 + 300^2) = sqrt(1300000) ≈ 1140.2
        String expected1 = "Zone 3: Center(1100, 300) Distance: 1140.2m";
        assertEquals(expected1, zone1.toString());

        Zone zone2 = new Zone(1, 0, 0, 100, 100);
        // Center: (50, 50), Distance: sqrt(5000) ≈ 70.7
        String result2 = zone2.toString();
        assertTrue(result2.contains("Zone 1"));
        assertTrue(result2.contains("Center(50, 50)"));
        assertTrue(result2.contains("70.7m"));
    }

    @Test
    @DisplayName("Should handle zones with odd dimensions")
    void testOddDimensions() {
        // Odd width and height should truncate in integer division
        Zone zone = new Zone(6, 0, 0, 101, 101);
        assertEquals(50, zone.getCenterX()); // (0 + 101) / 2 = 50
        assertEquals(50, zone.getCenterY());

        Zone zone2 = new Zone(7, 25, 35, 75, 95);
        assertEquals(50, zone2.getCenterX()); // (25 + 75) / 2 = 50
        assertEquals(65, zone2.getCenterY()); // (35 + 95) / 2 = 65
    }

    @Test
    @DisplayName("Should handle large coordinate values")
    void testLargeCoordinates() {
        Zone zone = new Zone(10, 0, 0, 10000, 10000);
        assertEquals(5000, zone.getCenterX());
        assertEquals(5000, zone.getCenterY());

        double expectedDistance = Math.sqrt(5000 * 5000 + 5000 * 5000);
        assertEquals(expectedDistance, zone.getDistanceFromBase(), 0.01);
    }

    @Test
    @DisplayName("Multiple zones should be independent")
    void testMultipleZones() {
        Zone zone1 = new Zone(1, 0, 0, 100, 100);
        Zone zone2 = new Zone(2, 200, 200, 400, 400);
        Zone zone3 = new Zone(3, 500, 0, 700, 200);

        assertEquals(1, zone1.getZoneId());
        assertEquals(2, zone2.getZoneId());
        assertEquals(3, zone3.getZoneId());

        assertEquals(50, zone1.getCenterX());
        assertEquals(300, zone2.getCenterX());
        assertEquals(600, zone3.getCenterX());

        assertNotEquals(zone1.getDistanceFromBase(), zone2.getDistanceFromBase());
        assertNotEquals(zone2.getDistanceFromBase(), zone3.getDistanceFromBase());
    }
}