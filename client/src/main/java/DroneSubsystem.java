import java.net.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import main.java.DroneState;

// DroneSubsystem simulates the drone's behavior
// Traveling to zones, Fighting fires and Returning to base
public class DroneSubsystem implements Runnable {
    private boolean running = true;

    // Constants from Iteration 0 calculations
    private static final double CRUISE_SPEED = 15.0;
    private static final double ACCELERATION = 2.5;
    private static final double DECELERATION = 2.0;
    private static final double WATER_DROP_RATE = 1.5;
    private static final double NOZZLE_TIME = 0.5;
    private static final double TANK_CAPACITY = 15.0;

    private static final String FAULT_NONE         = "NONE";
    private static final String FAULT_DRONE_STUCK  = "DRONE_STUCK";
    private static final String FAULT_NOZZLE_JAMMED = "NOZZLE_JAMMED";
    private static final String FAULT_PACKET_LOSS  = "PACKET_LOSS";
    private static final long STUCK_FREEZE_MS = 15000;

    //Iteration 2
    private volatile DroneState currentDroneState = DroneState.IDLE;
    private double currentWater = TANK_CAPACITY;
    private int currentZone = 0; //0 = base
    private int droneId;

    private double posX = 0, posY = 0;
    private double batteryLevel = 100.0;
    private static final double BATTERY_DRAIN_PER_METER = 0.005;

    private static final byte TYPE_DRONE_ASSIGNMENT = 0x02;
    private static final byte TYPE_DRONE_STATUS = 0x03;
    private static final byte TYPE_SHUTDOWN = 0x05;
    static final int PORT_DRONE_BASE = 6000;
    static final int PORT_SCHEDULER_DRONE = 5002;

    private final InetAddress schedulerAddress;
    private final int listenPort;
    private int currentZoneId = 0;
    private DroneState state = DroneState.IDLE;

    private String activeFault = FAULT_NONE;

    private static final int SPEEDUP = 100;

    // scheduler sends missions here
    //private BlockingQueue<FireEvent> missionQueue = new LinkedBlockingQueue<>();

    public DroneSubsystem(int droneId, InetAddress schedulerAddress) {
        this.droneId = droneId;
        this.schedulerAddress = schedulerAddress;
        this.listenPort = PORT_DRONE_BASE + droneId;
    }

    @Override
    public void run() {
        System.out.println("=== Drone #" + droneId + " STARTED ===");

        try (DatagramSocket socket = new DatagramSocket(listenPort)) {
            // No timeout — block indefinitely waiting for assignments
            System.out.println("[DRONE-" + droneId + "] Listening on port " + listenPort);

            // Register with Scheduler so it knows our address
            sendStatusPacket(socket, 0, "IDLE", "Drone online, ready for assignments", 0.0, FAULT_NONE);

            while (running) {
                // Block here until the next assignment arrives
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                try {
                    socket.receive(packet);
                } catch (SocketTimeoutException e) {
                    continue;
                }

                int len = packet.getLength();

                if (buffer[0] == TYPE_DRONE_ASSIGNMENT) {
                    // fields: [droneId, time, zoneId, eventType, severity, centerX, centerY]
                    String[] fields = new String(buffer, 1, len - 1).split(",");

                    if (Integer.parseInt(fields[0]) != droneId) {
                        continue;
                    }
                    String faultType = (fields.length > 7) ? fields[7] : FAULT_NONE;
                    FireEvent event = new FireEvent(fields[1], Integer.parseInt(fields[2]), fields[3], fields[4], faultType);
                    double centerX = Double.parseDouble(fields[5]);
                    double centerY = Double.parseDouble(fields[6]);
                    System.out.println("[DRONE-" + droneId + "] Assignment: " + event);

                    processAssignedFire(socket, event, centerX, centerY);

                } else if (buffer[0] == TYPE_SHUTDOWN) {
                    System.out.println("[DRONE-" + droneId + "] Shutdown received.");
                    break;
                }
            }
        } catch (Exception e) {
            System.out.println("[ERROR] Drone-" + droneId + ": " + e.getMessage());
        }

        System.out.println("=== Drone #" + droneId + " FINISHED ===");
    }

    // executed autonomously when a fire event is assigned (travel -> fight -> return)
    public void processAssignedFire(DatagramSocket socket, FireEvent event, double targetX, double targetY) {
        try {
            int zoneId = event.getZoneId();
            double waterNeeded = event.getWaterNeeded();
            double distance = Math.sqrt(targetX * targetX + targetY * targetY);
            double travelTime = calculateTravelTime(distance);
            double dropTime = calculateDropTime(waterNeeded);
            long travelMs = (long)(travelTime * 1000 / SPEEDUP);
            String severity = event.getSeverity();

            activeFault = event.getFaultType();

            System.out.println("[DRONE-" + droneId + "] ===================================");
            System.out.println("[DRONE-" + droneId + "] STARTING MISSION: Zone " + zoneId + " (" + severity + ") fault=" + activeFault);
            System.out.println("[DRONE-" + droneId + "] ===================================");

            transition(DroneState.EN_ROUTE);

            double actualUsed = 0;

            while (state != DroneState.IDLE) {
                switch (state) {
                    case EN_ROUTE:
                        try {
                            currentZone = zoneId;
                            if (FAULT_DRONE_STUCK.equals(activeFault)) {
                                System.out.println("[DRONE-" + droneId
                                        + "] FAULT: DRONE_STUCK — freezing mid-flight");
                                sendStatusPacket(socket, zoneId, "DRONE_STUCK",
                                        "Drone frozen mid-flight", 0.0, FAULT_DRONE_STUCK);
                                Thread.sleep(STUCK_FREEZE_MS); // scheduler watchdog fires here
                                transition(DroneState.FAULTED);
                                break;
                            }
                            animatePosition(socket, posX, posY, targetX, targetY, travelMs, activeFault);
                            transition(DroneState.ARRIVED);
                        } catch (Exception e) {
                            System.out.println("[Drone-" + droneId + "] Fault in EN_ROUTE: " + e.getMessage());
                            transition(DroneState.FAULTED);
                        }
                        break;

                    case ARRIVED:
                        try {
                            posX = targetX;
                            posY = targetY;
                            sendStatusPacket(socket, zoneId, "ARRIVED", "Arrived at Zone " + zoneId, 0.0, activeFault);
                            transition(DroneState.DROPPING_AGENT);
                        } catch (Exception e) {
                            System.out.println("[Drone-" + droneId + "] Fault in ARRIVED: " + e.getMessage());
                            transition(DroneState.FAULTED);
                        }
                        break;

                    case DROPPING_AGENT:
                        try {
                            if (FAULT_NOZZLE_JAMMED.equals(activeFault)) {
                                System.out.println("[DRONE-" + droneId
                                        + "] FAULT: NOZZLE_JAMMED — cannot open nozzle");
                                sendStatusPacket(socket, zoneId, "NOZZLE_JAMMED",
                                        "Nozzle jammed, drone going offline", 0.0,
                                        FAULT_NOZZLE_JAMMED);
                                transition(DroneState.FAULTED);
                                break;
                            }

                            actualUsed   = Math.min(waterNeeded, currentWater);
                            currentWater -= actualUsed;
                            sendStatusPacket(socket, zoneId, "EXTINGUISHING",
                                    "Dropping water at " + WATER_DROP_RATE + " L/s",
                                    0.0, activeFault);
                            Thread.sleep((long)(dropTime * 1000/SPEEDUP));

                            if (actualUsed >= waterNeeded) {
                                transition(DroneState.COMPLETED);
                            } else {
                                transition(DroneState.PARTIAL);
                            }
                        } catch (Exception e) {
                            System.out.println("[Drone-" + droneId + "] Fault in DROPPING_AGENT: " + e.getMessage());
                            transition(DroneState.FAULTED);
                        }
                        break;

                    case COMPLETED:
                        //If you completely extinguish the fire the amount of water used is the amount needed
                        sendStatusPacket(socket, zoneId, "COMPLETED", "Fire extinguished", actualUsed, FAULT_NONE);
                        transition(DroneState.RETURNING);
                        break;

                    case PARTIAL:
                        sendStatusPacket(socket, zoneId, "PARTIAL", String.format("Used %.1fL needs %.1fL more", actualUsed, waterNeeded - actualUsed), actualUsed, FAULT_NONE);
                        transition(DroneState.RETURNING);
                        break;

                    case RETURNING:
                        try {
                            sendStatusPacket(socket, zoneId, "RETURNING", "Returning to base", 0.0, FAULT_NONE);
                            // use zone 0 during return animation so GUI moves drone back to base
                            animatePosition(socket, posX, posY, 0, 0, travelMs, FAULT_NONE, 0);
                            currentWater = TANK_CAPACITY;
                            batteryLevel = 100.0;
                            currentZoneId = 0;
                            currentZone = 0;
                            posX = 0;
                            posY = 0;
                            transition(DroneState.IDLE);

                            // PACKET_LOSS fault: also drop the RETURNED packet so the scheduler
                            // watchdog fires and exercises the soft-fault recovery path.
                            // Without this fix the scheduler never loses track of the drone
                            // even though packets were dropped during flight.
                            if (FAULT_PACKET_LOSS.equals(activeFault)) {
                                System.out.println("[DRONE-" + droneId + "] FAULT: PACKET_LOSS — dropping RETURNED packet (scheduler watchdog will detect and soft-reset)");
                            } else {
                                sendStatusPacket(socket, 0, "RETURNED", "Refilled and ready", 0.0, FAULT_NONE);
                            }

                            System.out.println("[DRONE-" + droneId + "] MISSION COMPLETE. Waiting for next assignment.");
                        } catch (Exception e) {
                            System.out.println("[Drone-" + droneId + "] Fault in RETURNING: " + e.getMessage());
                            transition(DroneState.FAULTED);
                        }
                        break;

                    case FAULTED:
                        sendStatusPacket(socket, zoneId, "FAULTED",
                                "Fault: " + activeFault, 0.0, activeFault);

                        // soft faults return to base, hard faults (NOZZLE_JAMMED) stay put
                        if (!"NOZZLE_JAMMED".equals(activeFault)) {
                            try {
                                sendStatusPacket(socket, zoneId, "RETURNING",
                                        "Returning to base after fault", 0.0, activeFault);
                                animatePosition(socket, posX, posY, 0, 0,
                                        (long)(calculateTravelTime(Math.sqrt(posX * posX + posY * posY)) * 1000 / SPEEDUP),
                                        FAULT_NONE, 0);
                                posX = 0;
                                posY = 0;
                                currentWater = TANK_CAPACITY;
                                currentZoneId = 0;
                                currentZone = 0;
                                activeFault = FAULT_NONE;
                                state = DroneState.IDLE;
                                sendStatusPacket(socket, 0, "RETURNED",
                                        "Returned after fault", 0.0, FAULT_NONE);
                            } catch (Exception e) {
                                System.out.println("[Drone-" + droneId + "] Error returning after fault: " + e.getMessage());
                            }
                        } else {
                            running = false;
                            return;
                        }
                }
            }
        } catch (Exception e) {
            System.out.println("[ERROR] Drone-" + droneId + ": " + e.getMessage());
        }
    }

    private void transition(DroneState next) {
        System.out.println("[DRONE-" + droneId + "] " + state + " -> " + next);
        state = next;
    }

    // overload without explicit reportZone — uses currentZone (for outbound flight)
    private void animatePosition(DatagramSocket socket, double fromX, double fromY, double toX, double toY, long totalMs, String faultType) throws InterruptedException {
        animatePosition(socket, fromX, fromY, toX, toY, totalMs, faultType, currentZone);
    }

    // full version with explicit reportZone so return-to-base packets show zone=0 on the GUI
    private void animatePosition(DatagramSocket socket, double fromX, double fromY, double toX, double toY, long totalMs, String faultType, int reportZone) throws InterruptedException {
        int  steps  = 10;
        long stepMs = Math.max(1, totalMs / steps);

        double totalDistance = Math.sqrt(Math.pow(toX - fromX, 2) + Math.pow(toY - fromY, 2));
        double drainPerStep  = totalDistance * BATTERY_DRAIN_PER_METER / steps;

        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            posX = fromX + (toX - fromX) * t;
            posY = fromY + (toY - fromY) * t;

            batteryLevel = Math.max(0, batteryLevel - drainPerStep);

            // PACKET_LOSS — randomly drop ~50% of status packets
            boolean drop = FAULT_PACKET_LOSS.equals(faultType) && Math.random() < 0.5;
            if (!drop) {
                sendStatusPacket(socket, reportZone, state.name(),
                        String.format("pos=(%.0f;%.0f)", posX, posY),
                        0.0, faultType);
            } else {
                System.out.println("[DRONE-" + droneId + "] FAULT: PACKET_LOSS — dropped status packet at step " + i);
            }

            Thread.sleep(stepMs);
        }
    }

    private void sendStatusPacket(DatagramSocket socket, int zoneId, String status, String message, double waterUsed, String faultType) {
        try {
            byte[] payload = (droneId + "," + zoneId + "," + status + ","
                    + message + "," + waterUsed + "," + currentWater + ","
                    + posX + "," + posY + "," + faultType + ","
                    + String.format("%.1f", batteryLevel)).getBytes(); // NEW
            byte[] data = new byte[payload.length + 1];
            data[0] = TYPE_DRONE_STATUS;
            System.arraycopy(payload, 0, data, 1, payload.length);
            socket.send(new DatagramPacket(data, data.length,
                    schedulerAddress, PORT_SCHEDULER_DRONE));
        } catch (Exception e) {
            System.out.println("[ERROR] Drone-" + droneId + ": " + e.getMessage());
        }
    }

    private double calculateTravelTime(double distance) {
        double accelTime  = CRUISE_SPEED / ACCELERATION;
        double decelTime  = CRUISE_SPEED / DECELERATION;
        double accelDist  = 0.5 * ACCELERATION * accelTime * accelTime;
        double decelDist  = 0.5 * DECELERATION * decelTime * decelTime;
        double cruiseDist = distance - accelDist - decelDist;
        if (cruiseDist < 0) return Math.sqrt(2 * distance / (ACCELERATION + DECELERATION));
        return accelTime + cruiseDist / CRUISE_SPEED + decelTime;
    }

    private double calculateDropTime(double waterNeeded) {
        if (waterNeeded <= TANK_CAPACITY) return waterNeeded / WATER_DROP_RATE;
        return (TANK_CAPACITY / WATER_DROP_RATE) * (int) Math.ceil(waterNeeded / TANK_CAPACITY);
    }
}