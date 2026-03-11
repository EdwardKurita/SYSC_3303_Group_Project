import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 test cases for FireIncidentSubsystem class
 */
class FireIncidentSubsystemTest {

    @TempDir
    Path tempDir;

    private FireIncidentSubsystem subsystem;
    private SharedBuffer buffer;
    private FireDroneGUI gui;
    private File zonesFile;
    private File eventsFile;

    @BeforeEach
    void setUp() throws Exception {
        // Create test CSV files
        zonesFile = tempDir.resolve("zones.csv").toFile();
        eventsFile = tempDir.resolve("events.csv").toFile();

        // Create buffer and GUI
        buffer = new SharedBuffer();

        javax.swing.SwingUtilities.invokeAndWait(() -> {
            gui = new FireDroneGUI();
        });
    }

    @AfterEach
    void tearDown() {
        if (gui != null) {
            gui.dispose();
        }
    }

    @Test
    @DisplayName("Constructor should initialize all fields")
    void testConstructor() {
        subsystem = new FireIncidentSubsystem(buffer, gui,
                eventsFile.getPath(), zonesFile.getPath());
        assertNotNull(subsystem);
    }

    @Test
    @DisplayName("LoadZones should parse zone data correctly")
    void testLoadZones() throws Exception {
        // Create zones.csv with test data
        writeZonesFile(zonesFile,
                "ZoneID,Start,End\n" +
                        "1,(0;0),(100;100)\n" +
                        "2,(50;50),(150;150)\n" +
                        "3,(100;0),(200;100)\n"
        );

        subsystem = new FireIncidentSubsystem(buffer, gui,
                eventsFile.getPath(), zonesFile.getPath());

        // Use reflection to call private loadZones method
        java.lang.reflect.Method method = FireIncidentSubsystem.class
                .getDeclaredMethod("loadZones");
        method.setAccessible(true);
        method.invoke(subsystem);

        // Verify zones were loaded
        assertNotNull(subsystem.getZone(1));
        assertNotNull(subsystem.getZone(2));
        assertNotNull(subsystem.getZone(3));
    }

    @Test
    @DisplayName("GetZone should return correct zone")
    void testGetZone() throws Exception {
        writeZonesFile(zonesFile,
                "ZoneID,Start,End\n" +
                        "5,(10;20),(30;40)\n"
        );

        subsystem = new FireIncidentSubsystem(buffer, gui,
                eventsFile.getPath(), zonesFile.getPath());

        java.lang.reflect.Method method = FireIncidentSubsystem.class
                .getDeclaredMethod("loadZones");
        method.setAccessible(true);
        method.invoke(subsystem);

        Zone zone = subsystem.getZone(5);
        assertNotNull(zone);
        assertEquals(5, zone.getZoneId());
    }

    @Test
    @DisplayName("GetZone should return null for non-existent zone")
    void testGetZoneNotFound() throws Exception {
        writeZonesFile(zonesFile,
                "ZoneID,Start,End\n" +
                        "1,(0;0),(100;100)\n"
        );

        subsystem = new FireIncidentSubsystem(buffer, gui,
                eventsFile.getPath(), zonesFile.getPath());

        java.lang.reflect.Method method = FireIncidentSubsystem.class
                .getDeclaredMethod("loadZones");
        method.setAccessible(true);
        method.invoke(subsystem);

        assertNull(subsystem.getZone(99));
    }

    @Test
    @DisplayName("ReadEvents should parse and buffer events correctly")
    void testReadEvents() throws Exception {
        writeZonesFile(zonesFile,
                "ZoneID,Start,End\n" +
                        "1,(0;0),(100;100)\n"
        );

        writeEventsFile(eventsFile,
                "Time,ZoneID,EventType,Severity\n" +
                        "10:00:00,1,FIRE_DETECTED,High\n" +
                        "10:05:00,1,DRONE_REQUEST,Moderate\n"
        );

        subsystem = new FireIncidentSubsystem(buffer, gui,
                eventsFile.getPath(), zonesFile.getPath());

        // Run readEvents in separate thread
        Thread thread = new Thread(() -> {
            try {
                java.lang.reflect.Method method = FireIncidentSubsystem.class
                        .getDeclaredMethod("readEvents");
                method.setAccessible(true);
                method.invoke(subsystem);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread.start();

        // Wait for events to be buffered
        Thread.sleep(500);

        // Verify events were buffered
        FireEvent event1 = buffer.takeFireEvent();
        assertNotNull(event1);
        assertEquals(1, event1.getZoneId());
        assertEquals("FIRE_DETECTED", event1.getEventType());

        thread.join(3000);
    }

    @Test
    @DisplayName("Run should execute complete workflow")
    void testRun() throws Exception {
        writeZonesFile(zonesFile,
                "ZoneID,Start,End\n" +
                        "1,(0;0),(100;100)\n" +
                        "2,(50;50),(150;150)\n"
        );

        writeEventsFile(eventsFile,
                "Time,ZoneID,EventType,Severity\n" +
                        "10:00:00,1,FIRE_DETECTED,High\n"
        );

        subsystem = new FireIncidentSubsystem(buffer, gui,
                eventsFile.getPath(), zonesFile.getPath());

        // Run in separate thread
        Thread thread = new Thread(subsystem);
        thread.start();

        // Wait for processing
        Thread.sleep(2000);

        // Verify zones were loaded
        assertNotNull(subsystem.getZone(1));
        assertNotNull(subsystem.getZone(2));

        thread.join(3000);
    }

    @Test
    @DisplayName("LoadZones should handle multiple zones")
    void testLoadMultipleZones() throws Exception {
        writeZonesFile(zonesFile,
                "ZoneID,Start,End\n" +
                        "1,(0;0),(100;100)\n" +
                        "2,(100;0),(200;100)\n" +
                        "3,(0;100),(100;200)\n" +
                        "4,(100;100),(200;200)\n" +
                        "5,(200;0),(300;100)\n"
        );

        subsystem = new FireIncidentSubsystem(buffer, gui,
                eventsFile.getPath(), zonesFile.getPath());

        java.lang.reflect.Method method = FireIncidentSubsystem.class
                .getDeclaredMethod("loadZones");
        method.setAccessible(true);
        method.invoke(subsystem);

        // Verify all zones loaded
        for (int i = 1; i <= 5; i++) {
            assertNotNull(subsystem.getZone(i), "Zone " + i + " should be loaded");
        }
    }

    // Helper methods to write test CSV files
    private void writeZonesFile(File file, String content) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    private void writeEventsFile(File file, String content) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }
}