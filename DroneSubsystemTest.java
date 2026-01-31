import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 test cases for DroneSubsystem class
 */
class DroneSubsystemTest {

    private DroneSubsystem droneSubsystem;

    @BeforeEach
    void setUp() {
        // Create with null dependencies for testing calculation methods
        droneSubsystem = new DroneSubsystem(null, null, null);
    }

    @Test
    @DisplayName("Calculate travel time for short distance (no cruising phase)")
    void testCalculateTravelTimeShortDistance() throws Exception {
        // Use reflection to access private method
        java.lang.reflect.Method method = DroneSubsystem.class.getDeclaredMethod("calculateTravelTime", double.class);
        method.setAccessible(true);

        double distance = 50.0;
        double travelTime = (double) method.invoke(droneSubsystem, distance);

        assertTrue(travelTime > 0);
        assertTrue(travelTime < 10);
    }

    @Test
    @DisplayName("Calculate travel time for medium distance (with cruising)")
    void testCalculateTravelTimeMediumDistance() throws Exception {
        java.lang.reflect.Method method = DroneSubsystem.class.getDeclaredMethod("calculateTravelTime", double.class);
        method.setAccessible(true);

        double distance = 500.0;
        double travelTime = (double) method.invoke(droneSubsystem, distance);

        assertTrue(travelTime > 0);
        assertTrue(travelTime > 10);
    }

    @Test
    @DisplayName("Calculate drop time for water within tank capacity")
    void testCalculateDropTimeWithinCapacity() throws Exception {
        java.lang.reflect.Method method = DroneSubsystem.class.getDeclaredMethod("calculateDropTime", double.class);
        method.setAccessible(true);

        double waterNeeded = 10.0; // Less than 15.0L tank capacity
        double dropTime = (double) method.invoke(droneSubsystem, waterNeeded);

        // dropTime = waterNeeded / WATER_DROP_RATE (1.5 L/s)
        // 10.0 / 1.5 = 6.67 seconds
        assertEquals(6.67, dropTime, 0.01);
    }

    @Test
    @DisplayName("Calculate drop time for water exactly at tank capacity")
    void testCalculateDropTimeAtCapacity() throws Exception {
        java.lang.reflect.Method method = DroneSubsystem.class.getDeclaredMethod("calculateDropTime", double.class);
        method.setAccessible(true);

        double waterNeeded = 15.0; // Exactly tank capacity
        double dropTime = (double) method.invoke(droneSubsystem, waterNeeded);

        // dropTime = 15.0 / 1.5 = 10.0 seconds
        assertEquals(10.0, dropTime, 0.01);
    }

    @Test
    @DisplayName("Calculate drop time for water exceeding tank capacity (2 trips)")
    void testCalculateDropTimeExceedsCapacity() throws Exception {
        java.lang.reflect.Method method = DroneSubsystem.class.getDeclaredMethod("calculateDropTime", double.class);
        method.setAccessible(true);

        double waterNeeded = 30.0; // Exceeds 15.0L tank capacity (needs 2 trips)
        double dropTime = (double) method.invoke(droneSubsystem, waterNeeded);

        // 2 trips: (15.0 / 1.5) * 2 = 20.0 seconds
        assertEquals(20.0, dropTime, 0.01);
    }

    @Test
    @DisplayName("Calculate drop time for water requiring 3 trips")
    void testCalculateDropTimeThreeTrips() throws Exception {
        java.lang.reflect.Method method = DroneSubsystem.class.getDeclaredMethod("calculateDropTime", double.class);
        method.setAccessible(true);

        double waterNeeded = 40.0; // Needs 3 trips (15 + 15 + 10)
        double dropTime = (double) method.invoke(droneSubsystem, waterNeeded);

        // 3 trips: (15.0 / 1.5) * 3 = 30.0 seconds
        assertEquals(30.0, dropTime, 0.01);
    }
}