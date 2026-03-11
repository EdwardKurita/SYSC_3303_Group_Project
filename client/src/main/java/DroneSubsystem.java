import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

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


    //Iteration 2
    private volatile DroneState currentDroneState = DroneState.IDLE;
    private double currentWater = TANK_CAPACITY;
    private int currentZone = 0; //0 = base
    private int droneId;

    // scheduler sends missions here
    private BlockingQueue<FireEvent> missionQueue = new LinkedBlockingQueue<>();

    public DroneSubsystem(SharedBuffer buffer,
                          FireIncidentSubsystem fireSubsystem,
                          FireDroneGUI gui, int droneId) {
        this.buffer = buffer;
        this.fireSubsystem = fireSubsystem;
        this.gui = gui;
        this.droneId = droneId;
    }


    //Iteration 2 getters
    public DroneState getCurrentDroneState() { return currentDroneState;}
    public double getCurrentWater() { return currentWater;}
    public int getCurrentZone() { return currentZone;}
    public int getDroneId() { return droneId;}


    @Override
    public void run() {
        gui.log("=== Drone Subsystem STARTED ===");
        gui.updateDroneStatus("IDLE");

        try {
            // Wait for system to initialize
            gui.log("[DRONE-" + droneId + "] Initializing...");
            Thread.sleep(2000);
            gui.log("[DRONE-" + droneId + "] Ready. Waiting for fire assignments...");

            // Main loop: waits for Scheduler to call processAssignedFire()
            while (running) {
                //Block until a mission arrives
                FireEvent mission = missionQueue.take();

                // check for a mission shutdown (null)
                if (mission == null) {
                    gui.log("[DRONE-" + droneId + "] Shutdown signal received");
                    break;
                }

                //process the mission
                processAssignedFire(mission);
            }

        } catch (InterruptedException e) {
            gui.log("[DRONE-" + droneId +"] thread interrupted");
        }

        gui.log("=== Drone #" + droneId + " Subsystem FINISHED ===");
    }

    //scheduler calls this to assign fire
    public void assignFire(FireEvent event) {
        try{
            missionQueue.put(event);
            gui.log("[DRONE-" + droneId + "] Mission assignment recived for Zone " + event.getZoneId());
        }catch (InterruptedException e) {
            gui.log("[DRONE-" + droneId + "] Failed to queue mission");
        }
    }

    //shutdown
    public void shutdown(){
        try {
            missionQueue.put(null);
        } catch (InterruptedException e) {
            running = false;
        }
    }

    // executed autonomously when a fire event is assigned (travel -> fight -> return)
    public void processAssignedFire(FireEvent event) {
        try {
            int zoneId = event.getZoneId();
            String severity = event.getSeverity();

            gui.log("[DRONE-" + droneId + "]===================================");
            gui.log("[DRONE-" + droneId + "] STARTING MISSION: Zone " + zoneId + " (" + severity + ")");
            gui.log("[DRONE-" + droneId + "]===================================");

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

            gui.log("[DRONE-" + droneId + "] Mission parameters:");
            gui.log("[DRONE-" + droneId + "]    Distance: " + String.format("%.1f", distance) + "m");
            gui.log("[DRONE-" + droneId + "]    Travel time: " + String.format("%.1f", travelTime) + "s");
            gui.log("[DRONE-" + droneId + "]    Water needed: " + waterNeeded + "L");
            gui.log("[DRONE-" + droneId + "]    Water Available: " + currentWater + "L");


            // Step 1: Travel to zone
            gui.log("===================================");
            gui.log("[DRONE-" + droneId + "] PHASE 1: Traveling");
            transition(DroneState.EN_ROUTE);
            currentZone = zoneId;

            gui.log("[DRONE-" + droneId + "] -> Reporting to Scheduler: EN_ROUTE");
            sendResponse(zoneId, "EN_ROUTE",
                    String.format("Traveling to Zone %d (%.1fm, ETA: %.1fs)", zoneId, distance, travelTime), 0.0);

            //gui.log("[DRONE-" + droneId +"] Flying to Zone " + zoneId);
            // Simulate travel (10x faster for demo)
            Thread.sleep((long)(travelTime * 100));

            // Step 2: Arrive at zone
            gui.log("===================================");
            gui.log("[DRONE-" + droneId + "] PHASE 2: Arrived");
            transition(DroneState.ARRIVED);

            gui.log("[DRONE-" + droneId + "] -> Reporting to Scheduler: ARRIVED");
            sendResponse(zoneId, "ARRIVED",
                    "Arrived at Zone " + zoneId + ", opening nozzle...", 0.0);

            //gui.log("[DRONE] Arrived at Zone " + zoneId);
            // Simulate nozzle opening
            Thread.sleep((long)(NOZZLE_TIME * 1000));

            // Step 3: Fight fire
            gui.log("===================================");
            gui.log("[DRONE-" + droneId + "] PHASE 3: Extinguishing");
            transition(DroneState.DROPPING_AGENT);

            gui.log("[DRONE-" + droneId + "] -> Reporting to Scheduler: EXTINGUISHING");
            sendResponse(zoneId, "EXTINGUISHING",
                    "Dropping water at " + WATER_DROP_RATE + " L/s", 0.0);

            double actualWaterUsed = Math.min(waterNeeded, currentWater); //updates the amount of water the drone has
            currentWater -= actualWaterUsed;

            gui.log("[DRONE-" + droneId + "] Dropping " + actualWaterUsed + "L of water");
            // Simulate water drop
            Thread.sleep((long)(dropTime * 100));

            // Step 4: Fire extinguished
            gui.log("===================================");
            gui.log("[DRONE-" + droneId + "] PHASE 4: Assessment");
            transition(DroneState.COMPLETED);

            if(actualWaterUsed >= waterNeeded) {
                gui.log("[DRONE-" + droneId + "] Fire fully extinguished");
                gui.log("[DRONE-" + droneId + "] -> Reporting to Scheduler: COMPLETED");
                sendResponse(zoneId, "COMPLETED","Fire extinguished successfully", actualWaterUsed);
            } else {
                //set up for partially putting out a fire
                gui.log("[DRONE-" + droneId + "] Fire only partially extinguished");
                gui.log("[DRONE-" + droneId + "]    Used: " + actualWaterUsed + "L");
                gui.log("[DRONE-" + droneId + "]    Still Needs: " + (waterNeeded-actualWaterUsed) + "L");
                gui.log("[DRONE-" + droneId + "] -> Reporting to Scheduler: PARTIAL");
                sendResponse(zoneId, "PARTIAL",
                        String.format("Partial extinguish - used %.1fL, still needs %.1fL", actualWaterUsed, waterNeeded-actualWaterUsed), actualWaterUsed);
            }
            gui.log("[DRONE-" + droneId + "] Water remaining " + currentWater + "L");

            // Step 5: Return to base
            gui.log("===================================");
            gui.log("[DRONE-" + droneId + "] PHASE 5: RETURNING");
            transition(DroneState.RETURNING);

            gui.log("[DRONE-" + droneId + "] -> Reporting to Scheduler: RETURNING");
            sendResponse(zoneId, "RETURNING",
                    "Returning to base from zone" + zoneId, 0.0);

            //gui.log("[DRONE] Returning to base from Zone " + zoneId);
            // Simulate return travel
            Thread.sleep((long)(travelTime * 100));

            // Step 6: Back at base
            gui.log("===================================");
            gui.log("[DRONE-" + droneId + "] PHASE 6: COMPLETE");
            transition(DroneState.IDLE);
            currentWater = TANK_CAPACITY; // refill the tanks
            currentZone = 0; //reset the zone


            gui.log("[DRONE-" + droneId + "] Refilling water tank...");
            gui.log("[DRONE-" + droneId + "] Refill complete: " + TANK_CAPACITY + "L");

            gui.log("[DRONE-" + droneId + "] -> Reporting to Scheduler: RETURNED");
            sendResponse( zoneId, "RETURNED",
                    "returned to base, refilled and ready",TANK_CAPACITY);

            gui.log("[DRONE-" + droneId + "] STATUS: IDLE - Ready for mission");
            gui.log("[DRONE-" + droneId + "]===================================");
            gui.log("[DRONE-" + droneId + "] MISSION To ZONE " + zoneId + " COMPLETE");
            gui.log("[DRONE-" + droneId + "]===================================");


        } catch (InterruptedException e) {
            gui.log("[DRONE] Mission interrupted");
        } catch (Exception e) {
            gui.logError("[DRONE] Error: " + e.getMessage());
        }
    }

    //send status update to scheduler
    //one way communication
    public void sendResponse(int zoneId, String status, String message, double waterUsed) {
        try{
            DroneResponse response = new DroneResponse(zoneId,status,message,"Now",waterUsed);
            buffer.putDroneResponse(response);
        } catch (InterruptedException e) {
            gui.log("[DRONE-" + droneId + "] Failed to send response: " + status);
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

    //transition with logging
    private void transition(DroneState newState) {
        gui.log("[DRONE-" + droneId + "] State: " + currentDroneState + " → " + newState);
        currentDroneState = newState;
        gui.updateDroneStatus("Drone " + droneId + ": " + currentDroneState.toString());
    }
}