import main.java.DroneState;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

/**
 * Iteration 5 Test Cases — 10 tests
 * Covers capacity limits, performance metrics, and drone logic.
 *
 * @author Group 12 - Jiayi, Declan, Shael, Edward
 */
public class iter5Test {


    // TEST 1 — Drone runs out of water on High severity fire
    /**
     * A High severity fire needs 30L but drone only holds 15L.
     * Drone should not have enough water and should not complete the fire.
     * After returning to base it should be refilled to 15L.
     */
    @Test
    @DisplayName("Test 1 - Drone runs out of water on High severity fire (PARTIAL)")
    void testDronePartialOnHighSeverityFire() {
        DroneData drone = new DroneData(1);

        // Drone uses its full 15L tank
        drone.useWater(15.0);

        // Drone should now have 0 water
        assertEquals(0.0, drone.getCurrentWater(), 0.01,
                "Drone water should be 0 after using full tank");

        // Drone should not have enough water for another High severity fire
        assertFalse(drone.hasEnoughWater(30.0),
                "Drone with 0L should not handle High severity fire needing 30L");

        // After returning to base, drone should be refilled
        drone.returnToBase();
        assertEquals(15.0, drone.getCurrentWater(), 0.01,
                "Drone should be refilled to 15L after returning to base");

        // Drone should be IDLE after returning
        assertEquals(DroneState.IDLE, drone.getState(),
                "Drone should be IDLE after returning to base");
    }

    // TEST 2 — Drone completes Low severity fire with water remaining
    /**
     * A Low severity fire needs 10L and drone has 15L.
     * Drone should complete the mission and have 5L remaining.
     */
    @Test
    @DisplayName("Test 2 - Drone completes Low severity fire with water remaining")
    void testDroneCompletesLowSeverityFire() {
        DroneData drone = new DroneData(1);

        double waterNeeded = 10.0;
        double actualUsed  = Math.min(waterNeeded, drone.getCurrentWater());

        drone.useWater(actualUsed);

        // Should have used exactly 10L
        assertEquals(10.0, actualUsed, 0.01,
                "Drone should use exactly 10L for Low severity fire");

        // Should have 5L remaining
        assertEquals(5.0, drone.getCurrentWater(), 0.01,
                "Drone should have 5L remaining after Low severity fire");

        // Fire was fully extinguished
        assertTrue(actualUsed >= waterNeeded,
                "Drone should have fully extinguished the fire");
    }


    // TEST 3 — Drone with low water not dispatched for large fire
    /**
     * Drone has only 5L remaining after a previous mission.
     * It should not be dispatched for Moderate (20L) or High (30L) fires.
     * Only after refill should it be available again.
     */
    @Test
    @DisplayName("Test 3 - Drone with low water is not available for large fires")
    void testDroneNotDispatchedWithInsufficientWater() {
        DroneData drone = new DroneData(1);

        // Use 10L — only 5L remaining
        drone.useWater(10.0);

        assertFalse(drone.hasEnoughWater(20.0),
                "Drone with 5L should not handle Moderate fire needing 20L");
        assertFalse(drone.hasEnoughWater(30.0),
                "Drone with 5L should not handle High fire needing 30L");

        // After refill all checks should pass except High (15L tank < 30L)
        drone.refill();
        assertTrue(drone.hasEnoughWater(10.0),
                "Should handle Low after refill");
        assertTrue(drone.hasEnoughWater(15.0),
                "Should handle up to 15L after refill");
        assertFalse(drone.hasEnoughWater(30.0),
                "Single drone cannot handle High severity alone (tank is 15L)");
    }


    // TEST 4 — Refill restores full tank
    /**
     * After using water and calling refill(),
     * drone should have exactly 15L (TANK_CAPACITY) again.
     */
    @Test
    @DisplayName("Test 4 - Refill restores drone to full 15L tank capacity")
    void testDroneRefillRestoresFullTank() {
        DroneData drone = new DroneData(1);

        drone.useWater(12.0);
        assertEquals(3.0, drone.getCurrentWater(), 0.01,
                "Drone should have 3L after using 12L");

        drone.refill();
        assertEquals(15.0, drone.getCurrentWater(), 0.01,
                "Drone should have full 15L after refill");
    }


    // TEST 5 — Drone availability matches IDLE state only
    /**
     * A drone is only available for dispatch when in IDLE state.
     * In any other state it should not be dispatched.
     */
    @Test
    @DisplayName("Test 5 - Drone is only available when IDLE")
    void testDroneAvailabilityMatchesIdleState() {
        DroneData drone = new DroneData(1);

        assertTrue(drone.isAvailable(),
                "Drone should be available when IDLE");

        drone.setState(DroneState.EN_ROUTE);
        assertFalse(drone.isAvailable(),
                "Drone should not be available when EN_ROUTE");

        drone.setState(DroneState.DROPPING_AGENT);
        assertFalse(drone.isAvailable(),
                "Drone should not be available when DROPPING_AGENT");

        drone.setState(DroneState.RETURNING);
        assertFalse(drone.isAvailable(),
                "Drone should not be available when RETURNING");

        drone.setState(DroneState.FAULTED);
        assertFalse(drone.isAvailable(),
                "Drone should not be available when FAULTED");

        drone.returnToBase();
        assertTrue(drone.isAvailable(),
                "Drone should be available again after returnToBase()");
    }

    // TEST 6 — FireEvent water needed per severity
    /**
     * FireEvent.getWaterNeeded() must return the correct amounts
     * per the project spec: Low=10L, Moderate=20L, High=30L.
     */
    @Test
    @DisplayName("Test 6 - FireEvent returns correct water needed per severity")
    void testFireEventWaterNeededPerSeverity() {
        FireEvent high     = new FireEvent("14:00:00", 1, "FIRE_DETECTED", "High",     "NONE");
        FireEvent moderate = new FireEvent("14:00:00", 2, "FIRE_DETECTED", "Moderate", "NONE");
        FireEvent low      = new FireEvent("14:00:00", 3, "FIRE_DETECTED", "Low",      "NONE");

        assertEquals(30.0, high.getWaterNeeded(),     0.01, "High severity should need 30L");
        assertEquals(20.0, moderate.getWaterNeeded(), 0.01, "Moderate severity should need 20L");
        assertEquals(10.0, low.getWaterNeeded(),      0.01, "Low severity should need 10L");
    }

    // TEST 7 — Hard vs soft fault classification
    /**
     * isHardFault() should return true only for NOZZLE_JAMMED.
     * isSoftFault() should return true for DRONE_STUCK and PACKET_LOSS.
     * NONE should be neither.
     */
    @Test
    @DisplayName("Test 7 - DroneData correctly classifies hard and soft faults")
    void testDroneDataFaultClassification() {
        DroneData drone = new DroneData(1);

        drone.setFaultType("NOZZLE_JAMMED");
        assertTrue(drone.isHardFault(),  "NOZZLE_JAMMED should be a hard fault");
        assertFalse(drone.isSoftFault(), "NOZZLE_JAMMED should not be a soft fault");

        drone.setFaultType("DRONE_STUCK");
        assertFalse(drone.isHardFault(), "DRONE_STUCK should not be a hard fault");
        assertTrue(drone.isSoftFault(),  "DRONE_STUCK should be a soft fault");

        drone.setFaultType("PACKET_LOSS");
        assertFalse(drone.isHardFault(), "PACKET_LOSS should not be a hard fault");
        assertTrue(drone.isSoftFault(),  "PACKET_LOSS should be a soft fault");

        drone.setFaultType("NONE");
        assertFalse(drone.isHardFault(), "NONE should not be a hard fault");
        assertFalse(drone.isSoftFault(), "NONE should not be a soft fault");
    }


    // TEST 8 — Performance metrics: fire detection and extinguish
    /**
     * logFireDetected then logFireExtinguished should not throw
     * and printFinalReport should complete successfully.
     */
    @Test
    @DisplayName("Test 8 - Performance metrics records detection and extinguish without error")
    void testMetricsFireCycleDoesNotThrow() throws InterruptedException {
        PerformanceMetrics metrics = new PerformanceMetrics();

        metrics.logFireDetected(3, "High");
        Thread.sleep(50);
        metrics.logFireExtinguished(3);

        assertDoesNotThrow(metrics::printFinalReport,
                "Final report should print without errors after one fire cycle");
    }


    // TEST 9 — Performance metrics: drone utilization tracked
    /**
     * After a drone is dispatched and returns, utilization
     * should be recorded and be >= 0 for that drone.
     * Two drones should both appear in the utilization map.
     */
    @Test
    @DisplayName("Test 9 - Drone utilization tracked for multiple drones")
    void testMetricsDroneUtilizationTracked() throws InterruptedException {
        PerformanceMetrics metrics = new PerformanceMetrics();

        metrics.logDroneDispatched(1, 3);
        Thread.sleep(50);
        metrics.logDroneReturned(1);

        metrics.logDroneDispatched(2, 5);
        Thread.sleep(50);
        metrics.logDroneReturned(2);

        Map<Integer, Double> util = metrics.getUtilization();

        assertTrue(util.containsKey(1),
                "Drone 1 should appear in utilization report");
        assertTrue(util.containsKey(2),
                "Drone 2 should appear in utilization report");
        assertTrue(util.get(1) >= 0.0,
                "Drone 1 utilization should be non-negative");
        assertTrue(util.get(2) >= 0.0,
                "Drone 2 utilization should be non-negative");
    }


    // TEST 10 — Drone water never goes below zero
    /**
     * If more water is used than available, currentWater should
     * floor at 0 and never go negative.
     */
    @Test
    @DisplayName("Test 10 - Drone water level never goes below zero")
    void testDroneWaterNeverGoesNegative() {
        DroneData drone = new DroneData(1);

        // Try to use far more water than tank holds
        drone.useWater(100.0);

        assertEquals(0.0, drone.getCurrentWater(), 0.01,
                "Drone water should floor at 0, never go negative");
        assertTrue(drone.getCurrentWater() >= 0,
                "Drone water should always be non-negative");
    }
}