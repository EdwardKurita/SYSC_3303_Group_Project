import main.java.DroneState;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Date;
import java.net.*;

// Schedular coordinates between FireIncidentSubsystem and DroneSubsystem
// It reads fire events from the buffer and assigns them to the drone, then listens fpr drone responses and updates the GUI
public class Scheduler implements Runnable {
    // since we're switching off a shared buffer, we need to outline how packets and sockets are setup
    static final int PORT_FIRE_SERVER = 5000;
    static final int PORT_SCHEDULER_FIRE = 5001;
    static final int PORT_SCHEDULER_DRONE = 5002;
    static final int PORT_DRONE_BASE = 6000; // each drone has a port of 600x where x is the id.

    // packet types for preparing/receiving
    private static final byte TYPE_ZONE_DATA = 0x00;
    private static final byte TYPE_FIRE_EVENT = 0x01;
    private static final byte TYPE_DRONE_ASSIGNMENT = 0x02;
    private static final byte TYPE_DRONE_STATUS = 0x03;
    private static final byte TYPE_GUI_UPDATE = 0x04;
    private static final byte TYPE_SHUTDOWN = 0x05;

    private final Map<Integer, double[]> zoneBounds = new HashMap<>();

    private final InetAddress serverAddress;

    boolean running = true;
    private volatile boolean allEventsSent = false;

    private Queue<FireEvent> fireQueue = new LinkedList<>();
    private final Map<Integer, DroneData> droneData = new HashMap<>();
    private final Map<Integer, InetAddress> droneAddresses = new HashMap<>();
    private final Map<Integer, String> partialSeverity = new HashMap<>();

    private DatagramSocket fireSocket;
    private DatagramSocket droneSocket;

    private SchedulerState schedulerState = SchedulerState.WAITING;

    //watchdog — 8 seconds scaled time before assuming stuck/lost
    private static final long WATCHDOG_TIMEOUT_MS = 12000;

    // timestamp formatter for structured event logging
    private static final SimpleDateFormat TS = new SimpleDateFormat("HH:mm:ss.SSS");

    // Track performance data
    private PerformanceMetrics metrics = new PerformanceMetrics();

    public Scheduler(InetAddress serverAddress) {
        this.serverAddress = serverAddress;
    }

    @Override
    public void run() {
        System.out.println("=== Scheduler STARTED ===");
        System.out.println("Fire events  : port " + PORT_SCHEDULER_FIRE);
        System.out.println("Drone status : port " + PORT_SCHEDULER_DRONE);
        System.out.println("GUI updates  → " + serverAddress.getHostAddress() + ":" + PORT_FIRE_SERVER);

        try {
            fireSocket = new DatagramSocket(PORT_SCHEDULER_FIRE);
            droneSocket = new DatagramSocket(PORT_SCHEDULER_DRONE);

            // if we don't use these it will get stuck in the running loop
            // needs to jump out of waiting for a fire event or drone status
            // if there is none

            fireSocket.setReceiveBufferSize(512 * 1024);   // 512 KB
            droneSocket.setReceiveBufferSize(512 * 1024);

            fireSocket.setSoTimeout(1);
            droneSocket.setSoTimeout(1);

            while (running) {
                getFireEvents();
                getDroneStatus();

                switch (schedulerState) {
                    case WAITING:
                        if (!fireQueue.isEmpty()) {
                            transition(SchedulerState.DISPATCHING);
                        }
                        break;

                    case DISPATCHING:
                        boolean dispatched = dispatch();
                        transition(SchedulerState.MONITORING);
                        break;

                    case MONITORING:
                        checkWatchdog();
                        if (hasFault()) {
                            transition(SchedulerState.FAULT_HANDLING);
                        } else if (!fireQueue.isEmpty()) {
                            transition(SchedulerState.DISPATCHING);
                        } else if (!droneData.isEmpty() && allEventsSent && droneData.values().stream().allMatch(DroneData::isAvailable)) {
                            // All fires handled and all drones back at base — simulation complete
                            running = false;
                        }
                        break;

                    case FAULT_HANDLING:
                        handleFault();
                        transition(SchedulerState.DISPATCHING);
                        break;

                }
            }
        } catch (Exception e) {
            if (running) {
                System.out.println("[ERROR] Scheduler: " + e.getMessage());
            }
        }

        sendShutdown();

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

            int len = packet.getLength();

            if (data[0] == TYPE_ZONE_DATA) {
                String[] parsed = new String(data, 1, len - 1).split(",");

                zoneBounds.put(Integer.parseInt(parsed[0]), new double[]{Double.parseDouble(parsed[1]), Double.parseDouble(parsed[2]), Double.parseDouble(parsed[3]), Double.parseDouble(parsed[4])});
            } else if (data[0] == TYPE_FIRE_EVENT) {
                // [time, zoneId, eventType, severity]
                String[] parsed = new String(data, 1, len - 1).split(",");
                String faultType = (parsed.length > 4) ? parsed[4] : "NONE"; // NEW
                FireEvent event = new FireEvent(parsed[0], Integer.parseInt(parsed[1]),
                        parsed[2], parsed[3], faultType);
                log("FIRE_DETECTED zone=" + event.getZoneId() + " severity=" + event.getSeverity() + " fault=" + faultType);
                if (!reroute(event)) {
                    fireQueue.add(event);
                    System.out.println("Fire queued");
                }

            } else if (data[0] == TYPE_SHUTDOWN) {
                allEventsSent = true;
                log("All fire events received from FireIncidentSubsystem.");
            }

        } catch (SocketTimeoutException ignored) {
        } catch (Exception e) {
            System.out.println("[ERROR] Scheduler - getFireEvents: " + e.getMessage());
        }
    }

    private void getDroneStatus() {
        try {
            byte[] data = new byte[1024];
            DatagramPacket packet = new DatagramPacket(data, data.length);
            droneSocket.receive(packet);

            int len = packet.getLength();

            if (data[0] == TYPE_DRONE_STATUS) {
                String[] parsed = new String(data, 1, len - 1).split(",");
                int droneId = Integer.parseInt(parsed[0]);

                droneAddresses.putIfAbsent(droneId, packet.getAddress());
                droneData.putIfAbsent(droneId, new DroneData(droneId));

                DroneData drone = droneData.get(droneId);
                drone.updateFromResponse(parsed); // updateLastSeen() called inside

                // timestamp key lifecycle events
                switch (parsed[2]) {
                    case "ARRIVED":
                        log("ARRIVED    drone=" + droneId + " zone=" + parsed[1]);
                        break;
                    case "EXTINGUISHING":
                        log("EXTINGUISHING drone=" + droneId + " zone=" + parsed[1]);
                        break;
                    case "COMPLETED":
                        log("COMPLETED  drone=" + droneId + " zone=" + parsed[1] + " waterUsed=" + parsed[4] + "L");
                        // Record when fire is extinguished for metrics
                        metrics.logFireExtinguished(Integer.parseInt(parsed[1]));
                        break;
                    case "RETURNED":
                        log("RETURNED   drone=" + droneId + " — refilled and ready");
                        // Record drone return for metrics
                        metrics.logDroneReturned(droneId);
                        break;
                    case "FAULTED":
                    case "DRONE_STUCK":
                    case "NOZZLE_JAMMED":
                        log("FAULT      drone=" + droneId + " type=" + parsed[2]);
                        break;
                }

                if (drone.getCurrentMission() != null && parsed[2].equals("PARTIAL")) {
                    double remaining = drone.getCurrentMission().getWaterNeeded()
                            - Double.parseDouble(parsed[4]);
                    if (remaining >= 1) {
                        String severity;
                        if (remaining >= 25) severity = "High";
                        else if (remaining >= 15) severity = "Moderate";
                        else severity = "Low";
                        partialSeverity.put(droneId, severity);
                        fireQueue.add(new FireEvent("Now", Integer.parseInt(parsed[1]), "FIRE_DETECTED", severity, "NONE"));
                    }
                } else if (parsed[2].equals("RETURNED")) {
                    partialSeverity.remove(droneId);
                }

                // read faultType from parsed[8]
                String faultType = (parsed.length > 8) ? parsed[8] : "NONE";

                try (DatagramSocket guiUpdate = new DatagramSocket()) {
                    // faultType added as 8th field in GUI payload
                    String severityInfo = partialSeverity.containsKey(drone.getDroneId())
                            ? partialSeverity.get(drone.getDroneId())
                            : (parsed[2].equals("RETURNING") || parsed[2].equals("RETURNED")
                            ? "NONE"
                            : (drone.getCurrentMission() != null
                            ? drone.getCurrentMission().getSeverity()
                            : "NONE"));

                    //append utilization as field 9 so server can display it in live metrics
                    double utilization = metrics.getUtilization().getOrDefault(drone.getDroneId(), 0.0);


                    double battery = (parsed.length > 9) ? Double.parseDouble(parsed[9]) : 100.0; // NEW

                    byte[] payload = (drone.getDroneId() + "," + parsed[2] + ","
                            + drone.getPosX() + "," + drone.getPosY() + ","
                            + drone.getCurrentWater() + "," + drone.getCurrentZone() + ","
                            + severityInfo + "," + faultType + ","
                            + String.format("%.1f", utilization) + ","
                            + String.format("%.1f", battery)).getBytes(); // NEW
                    byte[] guiData = new byte[payload.length + 1];
                    guiData[0] = TYPE_GUI_UPDATE;
                    System.arraycopy(payload, 0, guiData, 1, payload.length);
                    guiUpdate.send(new DatagramPacket(guiData, guiData.length,
                            serverAddress, PORT_FIRE_SERVER));
                } catch (Exception e) {
                    System.out.println("[ERROR] Scheduler - getDroneStatus: " + e.getMessage());
                }
            }
        } catch (SocketTimeoutException ignored) {
        } catch (Exception e) {
            System.out.println("[ERROR] Scheduler - getDroneStatus: " + e.getMessage());
        }
    }

    private void checkWatchdog() {
        long now = System.currentTimeMillis();
        for (DroneData drone : droneData.values()) {
            if (drone.getState() != DroneState.IDLE
                    && drone.getState() != DroneState.FAULTED
                    && (now - drone.getLastSeenMs()) > WATCHDOG_TIMEOUT_MS) {
                log("WATCHDOG   drone=" + drone.getDroneId()
                        + " silent=" + (now - drone.getLastSeenMs()) + "ms — marking FAULTED (DRONE_STUCK)");
                drone.setFaultType("DRONE_STUCK");
                drone.setState(DroneState.FAULTED);
            }
        }
    }

    private boolean reroute(FireEvent newFire) {
        double[] bounds = zoneBounds.get(newFire.getZoneId());

        if (bounds == null) return false;

        DroneData candidate = null;
        for (DroneData drone : droneData.values()) {
            FireEvent curDroneMission = drone.getCurrentMission();
            if (curDroneMission != null
                    &&drone.getState() != DroneState.FAULTED
                    && drone.getState() != DroneState.RETURNING
                    && drone.getPosX() >= bounds[0] && drone.getPosX() <= bounds[2]
                    && drone.getPosY() >= bounds[1] && drone.getPosY() <= bounds[3]) {
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
            double[] rBounds = zoneBounds.getOrDefault(newFire.getZoneId(), new double[]{0, 0, 0, 0});
            double rCx = (rBounds[0] + rBounds[2]) / 2.0;
            double rCy = (rBounds[1] + rBounds[3]) / 2.0;
            byte[] payload = (candidate.getDroneId() + "," + newFire.getTime() + "," + newFire.getZoneId()
                    + "," + newFire.getEventType() + "," + newFire.getSeverity() + "," + rCx + "," + rCy
                    + "," + newFire.getFaultType()).getBytes();
            byte[] data = new byte[payload.length + 1];

            data[0] = TYPE_DRONE_ASSIGNMENT;
            System.arraycopy(payload, 0, data, 1, payload.length);

            assignment.send(new DatagramPacket(data, data.length, InetAddress.getByName("localhost"), port));

        } catch (Exception e) {
            System.out.println("[ERROR] Scheduler - dispatch: " + e.getMessage());
        }

        return true;
    }

    private boolean dispatch() {
        if (fireQueue.isEmpty()) {
            return false;
        }

        FireEvent event = fireQueue.peek();
        if (event == null) return false;

        DroneData candidate = null;

        for (DroneData drone : droneData.values()) {
            if (drone.isAvailable()) {
                if (candidate == null || drone.getBatteryLevel() > candidate.getBatteryLevel()) {
                    candidate = drone; // NEW: prefer drone with most battery
                }
            }
        }

        //No drone has enugh water yet -log and wait for refill
        if (candidate == null) {
            boolean anyIdle = droneData.values().stream().anyMatch(DroneData::isAvailable);
            if (anyIdle) {
                log("DISPATCH_WAIT fire=zone:" + event.getZoneId()
                        + " needs=" + event.getWaterNeeded() + "L"
                        + " - no drone has sufficient water, waiting for refill");
            }
            return false;
        }

        //remove fire from queue not that we have a suitable drone
        fireQueue.poll();

        System.out.println("================================================");
        System.out.println("DISPATCH");
        System.out.println("Drone: " + candidate.getDroneId());
        System.out.println("Now Servicing: " + event.getZoneId());
        System.out.println("Severity: " + event.getSeverity());
        System.out.println("===================================================");

        // Log when fire was detected for metrics
        metrics.logFireDetected(event.getZoneId(), event.getSeverity());

        // Log when drone is dispatched for metrics
        metrics.logDroneDispatched(candidate.getDroneId(), event.getZoneId());

        double[] bounds = zoneBounds.getOrDefault(event.getZoneId(), new double[]{0, 0, 0, 0});
        double cx = (bounds[0] + bounds[2]) / 2.0;
        double cy = (bounds[1] + bounds[3]) / 2.0;

        candidate.setCurrentMission(event);
        candidate.setState(DroneState.EN_ROUTE);

        log("DISPATCHED drone=" + candidate.getDroneId() + " → zone=" + event.getZoneId()
                + " severity=" + event.getSeverity() + " fault=" + event.getFaultType());

        // Send the mission to the drone
        try (DatagramSocket assignment = new DatagramSocket()) {
            int port = PORT_DRONE_BASE + candidate.getDroneId();
            byte[] payload = (candidate.getDroneId() + "," + event.getTime() + "," + event.getZoneId()
                    + "," + event.getEventType() + "," + event.getSeverity() + "," + cx + "," + cy
                    + "," + event.getFaultType()).getBytes();
            byte[] data = new byte[payload.length + 1];

            data[0] = TYPE_DRONE_ASSIGNMENT;
            System.arraycopy(payload, 0, data, 1, payload.length);

            assignment.send(new DatagramPacket(data, data.length, InetAddress.getByName("localhost"), port));

        } catch (Exception e) {
            System.out.println("[ERROR] Scheduler - dispatch: " + e.getMessage());
            return false;
        }
        return true;
    }

    private void transition(SchedulerState next) {
        System.out.println("[SCHEDULER] " + schedulerState + " -> " + next);
        schedulerState = next;
    }

    private boolean hasFault() {
        return droneData.values().stream().anyMatch(d -> d.getState() == DroneState.FAULTED);
    }

    private void handleFault() {
        // collect faulted drones first to avoid ConcurrentModificationException
        java.util.List<Integer> toRemove = new java.util.ArrayList<>();

        for (DroneData drone : droneData.values()) {
            if (drone.getState() == DroneState.FAULTED) {
                log("FAULT_HANDLE drone=" + drone.getDroneId() + " type=" + drone.getFaultType());

                // re-queue the mission so another drone can handle it
                if (drone.getCurrentMission() != null) {
                    // NEW: re-queue without the fault so another drone handles it normally
                    FireEvent original = drone.getCurrentMission();
                    FireEvent requeued = new FireEvent(original.getTime(), original.getZoneId(),
                            original.getEventType(), original.getSeverity(), "NONE");
                    fireQueue.add(requeued);

                    System.out.println("[SCHEDULER] Re-queued mission: " + original);

                    sendZoneStillActive(requeued);
                }

                // NEW: hard faults (NOZZLE_JAMMED) permanently remove the drone.
                // Soft faults (DRONE_STUCK, PACKET_LOSS) reset the drone to IDLE
                // so it re-enters the fleet and can take new assignments.
                if (drone.isHardFault()) {
                    log("HARD_FAULT drone=" + drone.getDroneId() + " (NOZZLE_JAMMED) permanently removed from fleet.");
                    toRemove.add(drone.getDroneId());
                } else {
                    log("SOFT_FAULT drone=" + drone.getDroneId() + " (" + drone.getFaultType() + ") reset to IDLE — returning to fleet.");
                    drone.setState(DroneState.IDLE);
                    drone.setFaultType("NONE");
                    drone.setCurrentMission(null);
                    drone.updateLastSeen();
                }
            }
        }

        for (int id : toRemove) {
            droneData.remove(id);
            droneAddresses.remove(id);
        }
    }

    private void sendZoneStillActive(FireEvent event) {
        try (DatagramSocket s = new DatagramSocket()) {
            double[] bounds = zoneBounds.getOrDefault(event.getZoneId(), new double[]{0,0,0,0});
            // Send a GUI update with status "QUEUED" so the zone stays coloured
            // Reuse an idle drone position (0,0) since no drone is assigned yet
            byte[] payload = ("0," + "QUEUED,"
                    + "0.0,0.0,0.0," + event.getZoneId() + ","
                    + event.getSeverity() + ",NONE,0.0,100.0").getBytes();
            byte[] data = new byte[payload.length + 1];
            data[0] = TYPE_GUI_UPDATE;
            System.arraycopy(payload, 0, data, 1, payload.length);
            s.send(new DatagramPacket(data, data.length, serverAddress, PORT_FIRE_SERVER));
        } catch (Exception e) {
            System.out.println("[ERROR] sendZoneStillActive: " + e.getMessage());
        }
    }

    /** Timestamped structured log line for key scheduler events. */
    private void log(String msg) {
        System.out.println("[" + TS.format(new Date()) + "] [SCHEDULER] " + msg);
    }

    /**
     * Sends a TYPE_SHUTDOWN packet to every known drone and to the GUI server.
     * Called once, just before the scheduler closes its sockets.
     */
    private void sendShutdown() {
        byte[] data = new byte[]{ TYPE_SHUTDOWN };

        try (DatagramSocket s = new DatagramSocket()) {
            // Notify every drone that registered with us
            for (Map.Entry<Integer, InetAddress> entry : droneAddresses.entrySet()) {
                int port = PORT_DRONE_BASE + entry.getKey();
                try {
                    s.send(new DatagramPacket(data, data.length, entry.getValue(), port));
                    log("SHUTDOWN sent to Drone " + entry.getKey() + " at port " + port);
                } catch (Exception e) {
                    System.out.println("[ERROR] sendShutdown drone-" + entry.getKey() + ": " + e.getMessage());
                }
            }

            // Notify the GUI receiver (GuiUpdateReceiver listens on PORT_FIRE_SERVER)
            try {
                s.send(new DatagramPacket(data, data.length, serverAddress, PORT_FIRE_SERVER));
                log("SHUTDOWN sent to GUI at " + serverAddress.getHostAddress() + ":" + PORT_FIRE_SERVER);
            } catch (Exception e) {
                System.out.println("[ERROR] sendShutdown GUI: " + e.getMessage());
            }

        } catch (Exception e) {
            System.out.println("[ERROR] sendShutdown: " + e.getMessage());
        }
    }

    // Add shutdown hook
    public void printFinalMetrics() {
        metrics.printFinalReport();
    }
}