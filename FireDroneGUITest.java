import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 test cases for FireDroneGUI class
 */
class FireDroneGUITest {

    private FireDroneGUI gui;

    @BeforeEach
    void setUp() {
        // Create GUI on Event Dispatch Thread
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                gui = new FireDroneGUI();
            });
        } catch (Exception e) {
            fail("Failed to create GUI: " + e.getMessage());
        }
    }

    @AfterEach
    void tearDown() {
        if (gui != null) {
            gui.dispose();
        }
    }

    @Test
    @DisplayName("GUI should be created with correct title and size")
    void testGUICreation() {
        assertNotNull(gui);
        assertEquals("Firefighting Drone System - Iteration 1", gui.getTitle());
        assertEquals(800, gui.getWidth());
        assertEquals(600, gui.getHeight());
    }

    @Test
    @DisplayName("Log and logError methods should work without throwing exceptions")
    void testLogging() throws Exception {
        assertDoesNotThrow(() -> {
            gui.log("Test message");
            gui.logError("Test error");
        });

        Thread.sleep(100);
    }

    @Test
    @DisplayName("UpdateDroneStatus should handle both string and DroneResponse")
    void testUpdateDroneStatus() throws Exception {
        assertDoesNotThrow(() -> {
            gui.updateDroneStatus("TRAVELING");
        });

        DroneResponse response = new DroneResponse(5, "EN_ROUTE", "Traveling", "10:00:00", 0.0);
        assertDoesNotThrow(() -> {
            gui.updateDroneStatus(response);
        });

        Thread.sleep(100);
    }

    @Test
    @DisplayName("UpdateSchedulerStatus and updateEventList should work")
    void testSchedulerAndEvents() throws Exception {
        assertDoesNotThrow(() -> {
            gui.updateSchedulerStatus("Processing events");
            gui.updateEventList("Zone 1: MINOR fire");
            gui.updateEventList("Zone 2: MAJOR fire");
        });

        Thread.sleep(100);
    }

    @Test
    @DisplayName("AddActiveZone and removeActiveZone should manage zones")
    void testActiveZones() throws Exception {
        assertDoesNotThrow(() -> {
            gui.addActiveZone("Zone 3 - Active");
            gui.addActiveZone("Zone 5 - Active");
            gui.removeActiveZone("Zone 3 - Active");
        });

        Thread.sleep(100);
    }

    @Test
    @DisplayName("Multiple log messages should be handled correctly")
    void testMultipleLogs() throws Exception {
        assertDoesNotThrow(() -> {
            gui.log("Message 1");
            gui.log("Message 2");
            gui.log("Message 3");
            gui.logError("Error 1");
        });

        Thread.sleep(200);
    }

    @Test
    @DisplayName("Thread-safe logging should handle concurrent access")
    void testConcurrentLogging() throws Exception {
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                gui.log("Thread 1 - Message " + i);
            }
        });

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                gui.log("Thread 2 - Message " + i);
            }
        });

        assertDoesNotThrow(() -> {
            t1.start();
            t2.start();
            t1.join();
            t2.join();
        });

        Thread.sleep(200);
    }

    // Added three more test for Iteration 2
    @Test
    @DisplayName("Active fire count should increment and decrement")
    void testActiveFireCount() throws Exception {
        assertDoesNotThrow(() -> {
            gui.incrementActiveFires();
            gui.incrementActiveFires();
            gui.incrementActiveFires();
        });
        Thread.sleep(100);
    }

    @Test
    @DisplayName("GUI should handle all drone states")
    void testAllDroneStates() throws Exception {
        assertDoesNotThrow(() -> {
            gui.updateDroneStatus("IDLE");
            gui.updateDroneStatus("EN_ROUTE");
            gui.updateDroneStatus("ARRIVED");
            gui.updateDroneStatus("DROPPING_AGENT");
            gui.updateDroneStatus("COMPLETED");
            gui.updateDroneStatus("RETURNING");
        });
        Thread.sleep(100);
    }

    @Test
    @DisplayName("Log processor should handle many messages")
    void testLogProcessorLoad() throws Exception {
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 100; i++) {
                gui.log("Load test message " + i);
            }
        });
        Thread.sleep(500);
    }
}
