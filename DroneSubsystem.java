// DroneSubsystem simulates the drone's behavior
// Traveling to zones, Fighting fires and Returning to base
public class DroneSubsystem implements Runnable {
    private SharedBuffer buffer;
    private FireIncidentSubsystem fireSubsystem;
    private FireDroneGUI gui;
    private boolean running = true;

    // Constants from Iteration 0 calculations
    private static final double CRUISE_SPEED = 15.0;
    private static final double ACCELERATION = 2.5;
    private static final double DECELERATION = 2.0;
    private static final double WATER_DROP_RATE = 1.5;
    private static final double NOZZLE_TIME = 0.5;
    private static final double TANK_CAPACITY = 15.0;

    public DroneSubsystem(SharedBuffer buffer,
                          FireIncidentSubsystem fireSubsystem,
                          FireDroneGUI gui) {
        this.buffer = buffer;
        this.fireSubsystem = fireSubsystem;
        this.gui = gui;
    }

    @Override
    public void run() {
        gui.log("=== Drone Subsystem STARTED ===");
        gui.updateDroneStatus("IDLE");

        try {
            // Wait for system to initialize
            gui.log("[DRONE] Initializing...");
            Thread.sleep(2000);
            gui.log("[DRONE] Ready. Waiting for fire assignments...");

            // Main loop: waits for Scheduler to call processAssignedFire()
            while (running) {
                Thread.sleep(100); // Small delay
            }

        } catch (InterruptedException e) {
            gui.log("[DRONE] Finished processing");
        }

        gui.log("=== Drone Subsystem FINISHED ===");
    }

    // Called by Scheduler when a fire event is assigned (travel -> fight -> return)
    public void processAssignedFire(FireEvent event) {
        try {
            int zoneId = event.getZoneId();
            String severity = event.getSeverity();

            gui.log("[DRONE] ===== STARTING MISSION: Zone " + zoneId + " (" + severity + ") =====");

            // Retrieve zone details from FireIncidentSubsystem
            Zone zone = fireSubsystem.getZone(zoneId);
            if (zone == null) {
                gui.logError("[DRONE] Zone " + zoneId + " not found!");
                return;
            }

            // Calculate mission parameters
            double waterNeeded = event.getWaterNeeded();
            double distance = zone.getDistanceFromBase();
            double travelTime = calculateTravelTime(distance);
            double dropTime = calculateDropTime(waterNeeded);

            // Step 1: Travel to zone
            DroneResponse routeResponse = new DroneResponse(
                    zoneId, "EN_ROUTE",
                    String.format("Traveling to Zone %d (%.1fm, ETA: %.1fs)", zoneId, distance, travelTime),
                    event.getTime(), 0.0
            );
            buffer.putDroneResponse(routeResponse);
            gui.log("[DRONE] " + routeResponse.getMessage());
            gui.updateDroneStatus("TRAVELING to Zone " + zoneId);
            // Simulate travel (10x faster for demo)
            Thread.sleep((long)(travelTime * 100));

            // Step 2: Arrive at zone
            // Consume the previous drone response to unblock the drone
            buffer.takeDroneResponse();
            // Create a new response to update drone status.
            DroneResponse arrivedResponse = new DroneResponse(
                    zoneId, "ARRIVED",
                    "Arrived at Zone " + zoneId + ", opening nozzle...",
                    "Now", 0.0
            );
            buffer.putDroneResponse(arrivedResponse);
            gui.log("[DRONE] Arrived at Zone " + zoneId);
            gui.updateDroneStatus("ARRIVED at Zone " + zoneId);
            // Simulate nozzle opening
            Thread.sleep((long)(NOZZLE_TIME * 1000));

            // Step 3: Fight fire
            buffer.takeDroneResponse();
            DroneResponse extinguishingResponse = new DroneResponse(
                    zoneId, "EXTINGUISHING",
                    "Dropping water at " + WATER_DROP_RATE + " L/s",
                    "Now", 0.0
            );
            buffer.putDroneResponse(extinguishingResponse);
            gui.log("[DRONE] Fighting " + severity + " fire in Zone " + zoneId);
            gui.updateDroneStatus("FIGHTING fire in Zone " + zoneId);

            double actualWaterUsed = Math.min(waterNeeded, TANK_CAPACITY);
            // Simulate water drop
            Thread.sleep((long)(dropTime * 100));

            // Step 4: Fire extinguished
            buffer.takeDroneResponse();
            DroneResponse completeResponse = new DroneResponse(
                    zoneId, "COMPLETED",
                    "Fire extinguished successfully",
                    "Completed", actualWaterUsed
            );
            buffer.putDroneResponse(completeResponse);
            gui.log("[DRONE] Fire extinguished in Zone " + zoneId);
            gui.updateDroneStatus("COMPLETED Zone " + zoneId);

            // Step 5: Return to base
            buffer.takeDroneResponse();
            gui.updateDroneStatus("RETURNING to base");
            gui.log("[DRONE] Returning to base from Zone " + zoneId);
            // Simulate return travel
            Thread.sleep((long)(travelTime * 100));

            // Step 6: Back at base
            gui.updateDroneStatus("IDLE at base");
            gui.log("[DRONE] Returned to base, ready for next mission");
            gui.log("[DRONE] ===== MISSION ZONE " + zoneId + " COMPLETE =====");

        } catch (InterruptedException e) {
            gui.log("[DRONE] Mission interrupted");
        } catch (Exception e) {
            gui.logError("[DRONE] Error: " + e.getMessage());
        }
    }

    // Calculate travel time based on acceleration, cruising and deceleration
    // Use equation from Iteration 0
    private double calculateTravelTime(double distance) {
        double accelTime = CRUISE_SPEED / ACCELERATION;
        double decelTime = CRUISE_SPEED / DECELERATION;

        double accelDistance = 0.5 * ACCELERATION * accelTime * accelTime;
        double decelDistance = 0.5 * DECELERATION * decelTime * decelTime;

        double cruiseDistance = distance - accelDistance - decelDistance;

        // If distance too short for full acceleration or deceleration
        if (cruiseDistance < 0) {
            return Math.sqrt(2 * distance / (ACCELERATION + DECELERATION));
        }

        double cruiseTime = cruiseDistance / CRUISE_SPEED;
        return accelTime + cruiseTime + decelTime;
    }

    // Calculates total time needed to drop required water
    // Accounts for tank capacity-may require multiple trips
    private double calculateDropTime(double waterNeeded) {
        if (waterNeeded <= TANK_CAPACITY) {
            return waterNeeded / WATER_DROP_RATE;
        } else {
            int trips = (int) Math.ceil(waterNeeded / TANK_CAPACITY);
            return (TANK_CAPACITY / WATER_DROP_RATE) * trips;
        }
    }
}