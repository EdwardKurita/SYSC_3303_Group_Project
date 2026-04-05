import org.junit.jupiter.api.*;
import javax.swing.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Iteration 5 Test Cases — 10 tests
 * Covers fault log, metrics panel, completed fires counter,
 * progress bar, DroneMarker faultType, and updateZoneFire
 *
 * @author Group 12 - Jiayi, Declan, Shael, Edward.
 */
public class iter5Test {

    // TEST 1 — Fault log panel exists and is empty on startup
    /**
     * The faultLogArea is a new panel added in Iteration 5.
     * It should exist and contain no text when the GUI first opens.
     */
    @Test
    @DisplayName("Test 1 - Fault log panel exists and is empty on startup")
    void testFaultLogExistsAndEmpty() throws Exception {
        FireDroneGUI gui = buildGui();

        JTextArea faultLog = field(gui, "faultLogArea");
        assertNotNull(faultLog,
                "faultLogArea must be present (new in iter 5)");
        assertEquals("", faultLog.getText().trim(),
                "Fault log should be empty on startup");

        gui.dispose();
    }


    // TEST 2 — Non-NONE faultType is written to the fault log
    /**
     * When a drone update arrives with faultType DRONE_STUCK,
     * the fault log should record it along with the drone id.
     */
    @Test
    @DisplayName("Test 2 - Non-NONE faultType is written to the fault log")
    void testFaultTypeWrittenToLog() throws Exception {
        FireDroneGUI gui = buildGui();

        gui.updateDroneMarker(1, "STUCK", 100, 100, 10, 1, "HIGH", "DRONE_STUCK");
        flushEDT();

        JTextArea faultLog = field(gui, "faultLogArea");
        assertTrue(faultLog.getText().contains("DRONE_STUCK"),
                "Fault log must record DRONE_STUCK");
        assertTrue(faultLog.getText().contains("1"),
                "Fault log must mention drone id");

        gui.dispose();
    }


    // TEST 3 — NONE faultType does NOT appear in the fault log
    /**
     * When a drone update arrives with faultType NONE,
     * the fault log should remain completely empty.
     */
    @Test
    @DisplayName("Test 3 - NONE faultType is not written to the fault log")
    void testNoneFaultNotLogged() throws Exception {
        FireDroneGUI gui = buildGui();

        gui.updateDroneMarker(2, "FLYING", 200, 200, 20, 1, "HIGH", "NONE");
        flushEDT();

        JTextArea faultLog = field(gui, "faultLogArea");
        assertEquals("", faultLog.getText().trim(),
                "Fault log must stay empty for NONE faults");

        gui.dispose();
    }


    // TEST 4 — Metrics panel exists and contains startup placeholder text
    /**
     * The metricsArea panel is new in Iteration 5.
     * On startup it should exist and show some placeholder content.
     */
    @Test
    @DisplayName("Test 4 - Metrics panel exists and contains startup placeholder text")
    void testMetricsPanelExistsWithPlaceholder() throws Exception {
        FireDroneGUI gui = buildGui();

        JTextArea metricsArea = field(gui, "metricsArea");
        assertNotNull(metricsArea,
                "metricsArea must be present (new in iter 5)");
        assertTrue(metricsArea.getText().length() > 0,
                "Metrics area should not be blank on startup");

        gui.dispose();
    }


    // TEST 5 — updateMetricsDisplay renders per-drone utilization
    /**
     * After calling updateMetricsDisplay with utilization data for two drones,
     * the metrics panel should display each drone's id and percentage.
     */
    @Test
    @DisplayName("Test 5 - updateMetricsDisplay renders per-drone utilization percentages")
    void testMetricsDisplayShowsUtilization() throws Exception {
        FireDroneGUI gui = buildGui();

        Map<Integer, Double> util = new LinkedHashMap<>();
        util.put(1, 80.0);
        util.put(2, 40.0);
        gui.updateMetricsDisplay(2, util);
        flushEDT();

        JTextArea metricsArea = field(gui, "metricsArea");
        String text = metricsArea.getText();
        assertTrue(text.contains("Drone 1"), "Should list Drone 1");
        assertTrue(text.contains("80"),      "Should show 80% for Drone 1");
        assertTrue(text.contains("Drone 2"), "Should list Drone 2");
        assertTrue(text.contains("40"),      "Should show 40% for Drone 2");

        gui.dispose();
    }


    // TEST 6 — createProgressBar at 50% has roughly half the bar filled
    /**
     * At 50% the bar should have an equal number of filled ('=')
     * and empty (' ') characters, within a tolerance of 3.
     */
    @Test
    @DisplayName("Test 6 - createProgressBar at 50% has roughly half the bar filled")
    void testProgressBarHalfFilled() throws Exception {
        FireDroneGUI gui = buildGui();

        String bar = progressBar(gui, 50.0);
        assertTrue(bar.startsWith("[") && bar.endsWith("]"),
                "Bar must be wrapped in [ ]");

        long filled = bar.chars().filter(c -> c == '=').count();
        long empty  = bar.chars().filter(c -> c == ' ').count();
        assertTrue(filled > 0, "Should have some filled chars");
        assertTrue(empty  > 0, "Should have some empty chars");
        assertTrue(Math.abs(filled - empty) <= 3,
                "Filled and empty halves should be roughly equal at 50%");

        gui.dispose();
    }


    // TEST 7 — completedFires does NOT increment for non-COMPLETED statuses
    /**
     * FLYING, RETURNING, and IDLE drone updates should leave
     * completedFires at 0.
     */
    @Test
    @DisplayName("Test 7 - completedFires does not increment for non-COMPLETED statuses")
    void testCompletedFiresNotIncrementedForOtherStatuses() throws Exception {
        FireDroneGUI gui = buildGui();

        gui.updateDroneMarker(1, "FLYING",    100, 100, 20, 1, "LOW",  "NONE");
        gui.updateDroneMarker(2, "RETURNING", 200, 200,  0, 1, "NONE", "NONE");
        gui.updateDroneMarker(3, "IDLE",      300, 300, 20, 2, "LOW",  "NONE");
        flushEDT();

        int completedFires = field(gui, "completedFires");
        assertEquals(0, completedFires,
                "completedFires must stay 0 for non-COMPLETED statuses");

        gui.dispose();
    }


    // TEST 8 — DroneMarker stores faultType as its new 8th field
    /**
     * The DroneMarker inner class gained a faultType field in Iteration 5.
     * It should be stored correctly and retrievable.
     */
    @Test
    @DisplayName("Test 8 - DroneMarker stores faultType as its new 8th field")
    void testDroneMarkerStoresFaultType() {
        FireDroneGUI.DroneMarker m = new FireDroneGUI.DroneMarker(
                5, "JAMMED", 0, 0, 5.0, 1, "MODERATE", "NOZZLE_JAMMED");

        assertEquals("NOZZLE_JAMMED", m.faultType,
                "faultType must be stored and retrievable (added in iter 5)");
    }


    // TEST 9 — updateZoneFire writes severity into activeFireSeverity map
    /**
     * updateZoneFire is a new method in Iteration 5.
     * It should write the given severity into the activeFireSeverity map
     * so the zone map panel can colour zones correctly.
     */
    @Test
    @DisplayName("Test 9 - updateZoneFire writes severity into activeFireSeverity map")
    void testUpdateZoneFireWritesSeverity() throws Exception {
        FireDroneGUI gui = buildGui();

        gui.updateZoneFire(1, "MODERATE");
        flushEDT();

        Map<Integer, String> sev = field(gui, "activeFireSeverity");
        assertEquals("MODERATE", sev.get(1),
                "activeFireSeverity must reflect the value passed to updateZoneFire");

        gui.dispose();
    }


//helpers
    private FireDroneGUI buildGui() throws Exception {
        List<FireIncidentZone> zones = List.of(new FireIncidentZone(1, 0, 0, 500, 500));
        CountDownLatch ready = new CountDownLatch(1);
        FireDroneGUI[] ref = new FireDroneGUI[1];
        SwingUtilities.invokeLater(() -> { ref[0] = new FireDroneGUI(zones); ready.countDown(); });
        ready.await(3, TimeUnit.SECONDS);
        return ref[0];
    }

    private void flushEDT() throws Exception {
        CountDownLatch l = new CountDownLatch(1);
        SwingUtilities.invokeLater(l::countDown);
        l.await(3, TimeUnit.SECONDS);
    }

    @SuppressWarnings("unchecked")
    private <T> T field(FireDroneGUI gui, String name) throws Exception {
        Field f = gui.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(gui);
    }

    private String progressBar(FireDroneGUI gui, double pct) throws Exception {
        Method m = gui.getClass().getDeclaredMethod("createProgressBar", double.class);
        m.setAccessible(true);
        return (String) m.invoke(gui, pct);
    }
}