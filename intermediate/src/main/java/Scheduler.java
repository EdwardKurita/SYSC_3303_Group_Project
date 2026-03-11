import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.io.*;
import java.net.*;

// Schedular coordinates between FireIncidentSubsystem and DroneSubsystem
// It reads fire events from the buffer and assigns them to the drone, then listens fpr drone responses and updates the GUI
public class Scheduler implements Runnable {
    // since we're switching off a shared buffer, we need to outline how packets and sockets are setup
    static final int PORT_FIRE_SERVER     = 5000;
    static final int PORT_SCHEDULER_FIRE  = 5001;
    static final int PORT_SCHEDULER_DRONE = 5002;
    static final int PORT_DRONE_BASE      = 6000; // each drone has a port of 600x where x is the id.

    // packet types for preparing/receiving
    private static final byte TYPE_FIRE_EVENT       = 0x01;
    private static final byte TYPE_DRONE_ASSIGNMENT = 0x02;
    private static final byte TYPE_DRONE_STATUS     = 0x03;
    private static final byte TYPE_GUI_UPDATE       = 0x04;
    private static final byte TYPE_SHUTDOWN         = 0x05;

    private final Map<Integer, double[]> zoneCentres = new HashMap<>();

    private final InetAddress serverAddress;
    private final int maxDrones;

    private boolean running = true;

    private Queue<FireEvent> fireQueue = new LinkedList<>();
    private final Map<Integer, DroneData> droneData = new HashMap<>();
    private final Map<Integer, InetAddress> droneAddresses = new HashMap<>();

    private DatagramSocket fireSocket;
    private DatagramSocket droneSocket;

    public Scheduler(InetAddress serverAddress, int maxDrones, Map<Integer, double[]> zoneCentres) {
        this.serverAddress = serverAddress;
        this.maxDrones = maxDrones;
        this.zoneCentres.putAll(zoneCentres);
    }

    @Override
    public void run() {
        System.out.println("=== Scheduler STARTED ===");
        System.out.println("Fire events  : port " + PORT_SCHEDULER_FIRE);
        System.out.println("Drone status : port " + PORT_SCHEDULER_DRONE);
        System.out.println("GUI updates  → " + serverAddress.getHostAddress() + ":" + PORT_FIRE_SERVER);
        System.out.println("Zone centres loaded: " + zoneCentres.size());

        try {
            fireSocket = new DatagramSocket(PORT_SCHEDULER_FIRE);
            droneSocket = new DatagramSocket(PORT_SCHEDULER_DRONE);

            // if we don't use these it will get stuck in the running loop
            // needs to jump out of waiting for a fire event or drone status
            // if there is none
            fireSocket.setSoTimeout(100);
            fireSocket.setSoTimeout(100);

            // timeout after 10 seconds
            fireSocket.setSoTimeout(10000);
            droneSocket.setSoTimeout(10000);

            while (running) {
                // get fire events
                // get drone statuses
                // dispatch a drone
            }
        } catch (Exception e) {
            if (running) {
                System.out.println("[ERROR] Scheduler: " + e.getMessage());
            }
        }

        if (fireSocket != null) {
            fireSocket.close();
        }

        if (droneSocket != null) {
            droneSocket.close();
        }

        System.out.println("=== Scheduler FINISHED ===");
    }

    private void getFireEvents() {
        try {
            byte[] data = new byte[1024];
            DatagramPacket packet = new DatagramPacket(data, data.length);
            fireSocket.receive(packet);

            int len =  packet.getLength();
            String received = new String(data, 0, len);

            if (received.charAt(0) == TYPE_FIRE_EVENT) {
                // [time, zoneId, eventType, severity]
                String[] parsed = new String(data, 1, len).split(",");

                FireEvent event = new FireEvent(parsed[0], Integer.parseInt(parsed[1]), parsed[2], parsed[3]);
                System.out.println("Fire event: " + event);

                // reroute drones
            }

        } catch (Exception e) {
            System.out.println("[ERROR] Scheduler - getFireEvents: " + e.getMessage());
        }
    }

    private void dispatchNextFire() {
        FireEvent event = fireQueue.poll();

        if  (event == null) {
            return;
        }

        //Iteration 2: select the only drone
        DroneData selectedDrone = droneInfo;

        gui.log("===================================");
        gui.log("[SCHEDULER] Assigning New Mission ");
        gui.log("[SCHEDULER]    Drone: #" +  selectedDrone.getDroneId());
        gui.log("[SCHEDULER]    Zone: " +  event.getZoneId());
        gui.log("[SCHEDULER]    Severity: " + event.getSeverity());
        gui.log("[SCHEDULER]    Water Needed: " +  event.getWaterNeeded() + "L");
        gui.log("[SCHEDULER]    Drone has: " +  selectedDrone.getCurrentWater() + "L");
        gui.log("===================================");

        //updates droneData
        selectedDrone.setCurrentMission(event);
        selectedDrone.setState(DroneState.EN_ROUTE);

        //send to drone
        gui.log("[SCHEDULER] -> Sending Command to Drone #" + selectedDrone.getDroneId());
        drone.assignFire(event);
        gui.log("[SCHEDULER] Command sent");
        gui.log("[SCHEDULER] Fires remaining in queue:" + fireQueue.size());
    }

    private void handleDroneResponse(DroneResponse response) {
        int zone = response.getZoneId();

        //update GUI
        gui.updateDroneStatus(response);

        //update drone data
        droneInfo.updateFromResponse(response);

        //log details based on status
        switch (response.getStatus()) {
            case "EN_ROUTE":
                gui.log("[SCHEDULER] Drone #" +  droneInfo.getDroneId() + " traveling to Zone " + zone);
                gui.log("[SCHEDULER] " + response.getMessage());
                break;

            case "ARRIVED":
                gui.log("[SCHEDULER] Drone #" +  droneInfo.getDroneId() + " arrived at Zone " + zone);
                gui.log("[SCHEDULER] Preparing to extinguish...");
                break;

            case "EXTINGUISHING":
                gui.log("[SCHEDULER] Drone #" +  droneInfo.getDroneId() + " extinguishing fire in Zone " + zone);
                gui.log("[SCHEDULER] " + response.getMessage());
                break;

            case "COMPLETED":
                gui.log("[SCHEDULER] Fire extinguished in Zone " + zone);
                gui.log("[SCHEDULER]    Water used: " + response.getWaterUsed() + "L");
                gui.log("[SCHEDULER]    Drone water remaining: " + droneInfo.getCurrentWater() + "L");
                gui.decrementActiveFires();
                break;

            case "PARTIAL":
                gui.log("[SCHEDULER] Fire partially extinguished in Zone " + zone);
                gui.log("[SCHEDULER] " + response.getMessage());
                reQueuePartialFire(response);
                break;

            case "RETURNING":
                gui.log("[SCHEDULER] Drone #" +  droneInfo.getDroneId() + " returning to base");
                break;

            case "RETURNED":
                gui.log("[SCHEDULER] Drone #" + droneInfo.getDroneId() + " returning to base and refilled");
                gui.log("[SCHEDULER] Drone ready for next mission");

                if (!fireQueue.isEmpty()) {
                    gui.log("[SCHEDULER] More missions in queue, dispatching...");
                } else {
                    gui.log("[SCHEDULER] No missions in queue, IDLE");
                }
                break;

            case "ERROR":
                gui.logError("[SCHEDULER] DRONE ERROR: " + response.getMessage());
                //could implement recovery logic
                break;

            default:
                gui.logError("[SCHEDULER] Unknown drone state: " + response.getStatus());
                break;
        }
    }

}