import java.io.*;
import java.net.*;
import java.util.*;

// FireIncidentSubsystem reads fire events and zone data from CSV files
public class FireIncidentSubsystem implements Runnable {
    static final int PORT_FIRE_SERVER = 5000; // gui updates
    static final int PORT_SCHEDULER_FIRE = 5001; // send fires

    // byte flags to add to and for parsing datapackets
    private static final byte TYPE_ZONE_DATA = 0x00;
    private static final byte TYPE_FIRE_EVENT = 0x01;
    private static final byte TYPE_SHUTDOWN = 0x05;

    private final Map<Integer, Zone> zones;
    private final List<FireIncidentZone> zonesList;

    private final FireDroneGUI gui;
    private final String eventFilePath;
    private final String zoneFilePath;

    private final InetAddress intermediate;

    // Constructor
    public FireIncidentSubsystem(FireDroneGUI gui, String eventFilePath, String zoneFilePath, InetAddress intermediate) {// CHANGE
        this.gui = gui;
        this.eventFilePath = eventFilePath;
        this.zoneFilePath = zoneFilePath;
        this.zones = new HashMap<>();
        this.zonesList = new ArrayList<>();
        this.intermediate = intermediate;
    }

    @Override
    public void run() {
        try {
            gui.log("=== Fire Incident Subsystem STARTED ===");
            loadZones(); // Step 1: Load zone coordinates
            sendZones();
            sendFireEvents();
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

    public static List<FireIncidentZone> loadZonesForGUI(String filename) {
        List<FireIncidentZone> zones = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");

                int id = Integer.parseInt(parts[0].trim());

                String[] startCoords = parts[1].trim().replace("(", "").replace(")", "").split(";");
                int x1 = Integer.parseInt(startCoords[0].trim());
                int y1 = Integer.parseInt(startCoords[1].trim());

                String[] endCoords = parts[2].trim().replace("(", "").replace(")", "").split(";");
                int x2 = Integer.parseInt(endCoords[0].trim());
                int y2 = Integer.parseInt(endCoords[1].trim());

                zones.add(new FireIncidentZone(id, x1, y1, x2, y2));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return zones;
    }

    private void sendZones() {
        try (DatagramSocket schedulerSocket = new DatagramSocket()) {
            for (Zone zone : zones.values()) {
                byte[] payload = (zone.getZoneId() + "," + zone.getStartX() + "," + zone.getStartY() + "," + zone.getEndX() + "," + zone.getEndY()).getBytes();
                byte[] data = new byte[payload.length + 1];
                data[0] = TYPE_ZONE_DATA;
                System.arraycopy(payload, 0, data, 1, payload.length);

                schedulerSocket.send(new DatagramPacket(data, data.length, intermediate, PORT_SCHEDULER_FIRE));
                gui.log("[ZONE] Sent Zone Data: " + new String(data));
            }
        } catch (Exception e) {
            System.out.println("[ERROR] FireIncidentSubsystem - sendZones" + e.getMessage());
        }
    }

    private void sendFireEvents() {
        try (DatagramSocket schedulerSocket = new DatagramSocket(); BufferedReader br = new BufferedReader(new FileReader(eventFilePath))) {
            br.readLine(); // get the headerr
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");

                String time = parts[0].trim();
                int zoneId = Integer.parseInt(parts[1].trim());
                String eventType = parts[2].trim();
                String severity = parts[3].trim();

                FireEvent event = new FireEvent(time, zoneId, eventType, severity);
                gui.log("[FIRE] Detected: " + event);
                gui.updateEventList(event.toString());
                gui.incrementActiveFires();

                byte[] payload = (time +  "," + zoneId + "," + eventType + "," + severity).getBytes();
                byte[] data = new byte[payload.length + 1];
                data[0] = TYPE_FIRE_EVENT;
                System.arraycopy(payload, 0, data, 1, payload.length);

                schedulerSocket.send(new DatagramPacket(data, data.length, intermediate, PORT_SCHEDULER_FIRE));
                gui.log("[FIRE] Sent Fire Event Data: " + new String(data));
            }
        } catch (Exception e) {
            System.out.println("[ERROR] FireIncidentSubsystem - sendFireEvents" + e.getMessage());
        }
    }
}
