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
    private final Map<Integer, double[]> zoneBounds = new HashMap<>();

    private final InetAddress serverAddress;
    private final int maxDrones;

    private boolean running = true;

    private Queue<FireEvent> fireQueue = new LinkedList<>();
    private final Map<Integer, DroneData> droneData = new HashMap<>();
    private final Map<Integer, InetAddress> droneAddresses = new HashMap<>();

    private DatagramSocket fireSocket;
    private DatagramSocket droneSocket;

    public Scheduler(InetAddress serverAddress, int maxDrones, Map<Integer, double[]> zoneCentres, Map<Integer, double[]> zoneBounds) {
        this.serverAddress = serverAddress;
        this.maxDrones = maxDrones;
        this.zoneCentres.putAll(zoneCentres);
        this.zoneBounds.putAll(zoneBounds);
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
                getFireEvents();
                getDroneStatus();
                dispatch();
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

                if (!reroute(event)) {
                    fireQueue.add(event);
                    System.out.println("Fire queued");
                }

            } else if (received.charAt(0) == TYPE_SHUTDOWN) {
                running = false;
                System.out.println("Shut down triggered");
            }

        } catch (Exception e) {
            System.out.println("[ERROR] Scheduler - getFireEvents: " + e.getMessage());
        }
    }

    private void getDroneStatus() {
        try {
            byte[] data = new byte[1024];
            DatagramPacket packet = new DatagramPacket(data, data.length);
            droneSocket.receive(packet);

            int len =  packet.getLength();
            String received = new String(data, 0, len);

            if (received.charAt(0) == TYPE_DRONE_STATUS) {
                String[] parsed = new String(data, 1, len).split(",");

                int droneId = Integer.parseInt(parsed[0]);

                // add new drones to the system if there are some
                droneAddresses.putIfAbsent(droneId, packet.getAddress());
                droneData.putIfAbsent(droneId, new DroneData(droneId));

                DroneData drone = droneData.get(droneId);
                drone.updateFromResponse(parsed);

                double remaining = drone.getCurrentMission().getWaterNeeded() - Double.parseDouble(parsed[4]);
                if (parsed[2].equals("PARTIAL") && remaining >= 1) {
                    String severity;
                    if (remaining >= 25) {
                        severity = "High";
                    } else if (remaining >= 15) {
                        severity = "Moderate";
                    } else {
                        severity = "Low";
                    }
                    fireQueue.add(new FireEvent(parsed[0], Integer.parseInt(parsed[1]), parsed[2], severity));
                }

                try (DatagramSocket guiupdate = new DatagramSocket()) {
                    byte[] payload = (drone.getDroneId() + "," + parsed[2] + "," + drone.getPosX() + "," + drone.getPosY() + "," + drone.getCurrentWater() + "," + drone.getCurrentZone() + "," + ((drone.getCurrentMission() != null) ? drone.getCurrentMission().getSeverity() : "NONE")).getBytes();
                    byte[] guidata = new byte[payload.length + 1];

                    guidata[0] = TYPE_GUI_UPDATE;
                    System.arraycopy(payload, 0, guidata, 1, payload.length);

                    guiupdate.send(new DatagramPacket(guidata, guidata.length, serverAddress, PORT_FIRE_SERVER));

                } catch (Exception e) {
                    System.out.println("[ERROR] Scheduler - getDroneStatus: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.out.println("[ERROR] Scheduler - getDroneStatus: " + e.getMessage());
        }
    }

    private boolean reroute(FireEvent newFire) {
        double[] bounds = zoneBounds.get(newFire.getZoneId());

        DroneData candidate = null;
        for (DroneData drone : droneData.values()) {
            FireEvent curDroneMission = drone.getCurrentMission();
            if (curDroneMission != null && drone.getPosX() >= bounds[0] && drone.getPosX() <= bounds[2] && drone.getPosY() >= bounds[1] && drone.getPosY() <= bounds[3] ) {
                candidate = drone;
                break;
            }
        }

        if (candidate == null) {
            return false;
        }

        System.out.println("===================================================");
        System.out.println("REROUTE");
        System.out.println("Drone: " + candidate.getDroneId());
        System.out.println("Now Servicing: " + newFire.getZoneId());
        System.out.println("Re-inserting interrupted mission to queue");
        System.out.println("===================================================");

        fireQueue.add(candidate.getCurrentMission());

        candidate.setCurrentMission(newFire);
        candidate.setState(DroneState.EN_ROUTE);

        // Send the mission to the drone
        try (DatagramSocket assignment = new DatagramSocket()) {
            int port = PORT_DRONE_BASE + candidate.getDroneId();
            byte[] payload = (candidate.getDroneId() + "," + newFire.getTime() + "," +  newFire.getZoneId() + "," + newFire.getEventType() + "," + newFire.getSeverity()).getBytes();
            byte[] data = new byte[payload.length + 1];

            data[0] = TYPE_DRONE_ASSIGNMENT;
            System.arraycopy(payload, 0, data, 1, payload.length);

            assignment.send(new DatagramPacket(data, data.length, InetAddress.getByName("localhost"), port));

        } catch (Exception e) {
            System.out.println("[ERROR] Scheduler - dispatch: " + e.getMessage());
        }

        return true;
    }

    private void dispatch() {
        if (fireQueue.isEmpty()) {
            return;
        }

        DroneData candidate = null;

        for (DroneData drone : droneData.values()) {
            if (drone.isAvailable()) {
                candidate = drone;
                break;
            }
        }

        if (candidate == null) {
            return;
        }

        FireEvent event =  fireQueue.poll();

        System.out.println("================================================");
        System.out.println("DISPATCH");
        System.out.println("Drone: " + candidate.getDroneId());
        System.out.println("Now Servicing: " + event.getZoneId());
        System.out.println("Severity: " + event.getSeverity());
        System.out.println("===================================================");

        candidate.setCurrentMission(event);
        candidate.setState(DroneState.EN_ROUTE);

        // Send the mission to the drone
        try (DatagramSocket assignment = new DatagramSocket()) {
            int port = PORT_DRONE_BASE + candidate.getDroneId();
            byte[] payload = (candidate.getDroneId() + "," + event.getTime() + "," +  event.getZoneId() + "," + event.getEventType() + "," + event.getSeverity()).getBytes();
            byte[] data = new byte[payload.length + 1];

            data[0] = TYPE_DRONE_ASSIGNMENT;
            System.arraycopy(payload, 0, data, 1, payload.length);

            assignment.send(new DatagramPacket(data, data.length, InetAddress.getByName("localhost"), port));

        } catch (Exception e) {
            System.out.println("[ERROR] Scheduler - dispatch: " + e.getMessage());
        }
    }
}