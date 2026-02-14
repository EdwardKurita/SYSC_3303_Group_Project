import java.io.*;
import java.util.*;

// FireIncidentSubsystem reads fire events and zone data from CSV files
public class FireIncidentSubsystem implements Runnable {
    private SharedBuffer buffer;
    private Map<Integer, Zone> zones;
    private FireDroneGUI gui;
    private String eventFilePath;
    private String zoneFilePath;

    // Constructor
    public FireIncidentSubsystem(SharedBuffer buffer,
                                 FireDroneGUI gui,
                                 String eventFilePath,
                                 String zoneFilePath) {
        this.buffer = buffer;  // CHANGE
        this.gui = gui;
        this.eventFilePath = eventFilePath;
        this.zoneFilePath = zoneFilePath;
        this.zones = new HashMap<>();
    }

    @Override
    public void run() {
        try {
            gui.log("=== Fire Incident Subsystem STARTED ===");
            loadZones(); // Step 1: Load zone coordinates
            readEvents(); // Step 2: Read and buffer fire events
            gui.log("=== Fire Incident Subsystem FINISHED ===");

        } catch (Exception e) {
            gui.logError("Error in FireIncidentSubsystem: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Loads zone data fron zones.csv (x1;y1) (x2;y2)
    private void loadZones() throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(zoneFilePath));
        String line = reader.readLine(); // Skip header

        while ((line = reader.readLine()) != null) {
            String[] parts = line.split(",");
            int zoneId = Integer.parseInt(parts[0].trim());

            // Parse start coordinates
            String startStr = parts[1].trim().replace("(", "").replace(")", "");
            String[] startCoords = startStr.split(";");
            int startX = Integer.parseInt(startCoords[0]);
            int startY = Integer.parseInt(startCoords[1]);

            // Parse end coordinates
            String endStr = parts[2].trim().replace("(", "").replace(")", "");
            String[] endCoords = endStr.split(";");
            int endX = Integer.parseInt(endCoords[0]);
            int endY = Integer.parseInt(endCoords[1]);

            // Create and store Zone obj
            Zone zone = new Zone(zoneId, startX, startY, endX, endY);
            zones.put(zoneId, zone);
            gui.log("Loaded: " + zone);
        }
        reader.close();
        gui.log("Total zones loaded: " + zones.size());
    }

    // Read fire events from event.csv and put them into the buffer
    private void readEvents() throws IOException, InterruptedException {
        BufferedReader reader = new BufferedReader(new FileReader(eventFilePath));
        String line = reader.readLine(); // Skip header

        while ((line = reader.readLine()) != null) {
            String[] parts = line.split(",");
            String time = parts[0].trim();
            int zoneId = Integer.parseInt(parts[1].trim());
            String eventType = parts[2].trim();
            String severity = parts[3].trim();

            // Create FireEvent and log it
            FireEvent event = new FireEvent(time, zoneId, eventType, severity);
            gui.log("[FIRE] Detected: " + event);
            gui.updateEventList(event.toString());
            gui.incrementActiveFires();

            // Put event into Synchronized buffer
            buffer.putFireEvent(event);
            // Delay to simulate time between fire detections
            Thread.sleep(1000);
        }
        reader.close();
    }

    // Return the Zone obj for a given zoneId
    public Zone getZone(int zoneId) {
        return zones.get(zoneId);
    }
}
