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

    //Iteration 2
    private volatile DroneState currentDroneState = DroneState.IDLE;
    private double currentWater = TANK_CAPACITY;
    private int currentZone = 0; //0 = base
    private int droneId;

    private double posX = 0, posY = 0;

    private static final byte TYPE_DRONE_ASSIGNMENT = 0x02;
    private static final byte TYPE_DRONE_STATUS = 0x03;
    private static final byte TYPE_SHUTDOWN = 0x05;
    static final int PORT_DRONE_BASE = 6000;
    static final int PORT_SCHEDULER_DRONE = 5002;

    private final InetAddress schedulerAddress;
    private final int listenPort;
    private int currentZoneId = 0;
    private DroneState state = DroneState.IDLE;

    // scheduler sends missions here
    private BlockingQueue<FireEvent> missionQueue = new LinkedBlockingQueue<>();

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
            sendStatusPacket(socket, 0, "IDLE", "Drone online, ready for assignments", 0.0);

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

                    FireEvent event = new FireEvent(fields[1], Integer.parseInt(fields[2]), fields[3], fields[4]);
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
            long travelMs = (long)(travelTime * 100); // 10x speed-up
            String severity = event.getSeverity();

            System.out.println("[DRONE-" + droneId + "]===================================");
            System.out.println("[DRONE-" + droneId + "] STARTING MISSION: Zone " + zoneId + " (" + severity + ")");
            System.out.println("[DRONE-" + droneId + "]===================================");

            transition(DroneState.EN_ROUTE);

            double actualUsed = 0;

            while (state != DroneState.IDLE) {
                switch (state) {
                    case EN_ROUTE:
                        try {
                            currentZone = zoneId;
                            animatePosition(socket, posX, posY, targetX, targetY, travelMs);
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
                            sendStatusPacket(socket, zoneId, "ARRIVED", "Arrived at Zone " + zoneId, 0.0);
                            transition(DroneState.DROPPING_AGENT);
                        } catch (Exception e) {
                            System.out.println("[Drone-" + droneId + "] Fault in ARRIVED: " + e.getMessage());
                            transition(DroneState.FAULTED);
                        }
                        break;

                    case DROPPING_AGENT:
                        try {
                            actualUsed = Math.min(waterNeeded, currentWater);
                            currentWater -= actualUsed;
                            sendStatusPacket(socket, zoneId, "EXTINGUISHING", "Dropping water at " + WATER_DROP_RATE + " L/s", 0.0);
                            Thread.sleep((long) (dropTime * 100));
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
                        sendStatusPacket(socket, zoneId, "COMPLETED", "Fire extinguished", actualUsed);
                        transition(DroneState.RETURNING);
                        break;

                    case PARTIAL:
                        sendStatusPacket(socket, zoneId, "PARTIAL", String.format("Used %.1fL needs %.1fL more", actualUsed, waterNeeded - actualUsed), actualUsed);
                        transition(DroneState.RETURNING);
                        break;

                    case RETURNING:
                        try {
                            sendStatusPacket(socket, zoneId, "RETURNING", "Returning to base", 0.0);
                            animatePosition(socket, posX, posY, 0, 0, travelMs);
                            currentWater = TANK_CAPACITY;
                            currentZoneId = 0;
                            posX = 0;
                            posY = 0;
                            transition(DroneState.IDLE);
                            sendStatusPacket(socket, 0, "RETURNED", "Refilled and ready", 0.0);
                            System.out.println("[DRONE-" + droneId + "] MISSION COMPLETE. Waiting for next assignment.");
                        } catch (Exception e) {
                            System.out.println("[Drone-" + droneId + "] Fault in RETURNING: " + e.getMessage());
                            transition(DroneState.FAULTED);
                        }
                        break;

                    case FAULTED:
                        sendStatusPacket(socket, zoneId, "FAULTED", "Something Has Happened", 0.0);
                        break;
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

    private void animatePosition(DatagramSocket socket, double fromX, double fromY, double toX, double toY, long totalMs) throws InterruptedException {
        int  steps  = 10;
        long stepMs = Math.max(1, totalMs / steps);

        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            posX = fromX + (toX - fromX) * t;
            posY = fromY + (toY - fromY) * t;

            sendStatusPacket(socket, currentZoneId, state.name(), String.format("pos=(%.0f;%.0f)", posX, posY), 0.0);
            Thread.sleep(stepMs);
        }
    }

    private void sendStatusPacket(DatagramSocket socket, int zoneId, String status, String message, double waterUsed) {
        try {
            byte[] payload = (droneId + "," + zoneId + "," + status + "," + message + "," + waterUsed + "," + currentWater + "," + posX + "," + posY).getBytes();
            byte[] data = new byte[payload.length + 1];
            data[0] = TYPE_DRONE_STATUS;

            System.arraycopy(payload, 0, data, 1, payload.length);

            socket.send(new DatagramPacket(data, data.length, schedulerAddress, PORT_SCHEDULER_DRONE));
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