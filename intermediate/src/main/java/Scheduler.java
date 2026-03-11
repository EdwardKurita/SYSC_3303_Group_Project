import java.util.LinkedList;
import java.util.Queue;

// Schedular coordinates between FireIncidentSubsystem and DroneSubsystem
// It reads fire events from the buffer and assigns them to the drone, then listens fpr drone responses and updates the GUI
public class Scheduler implements Runnable {
    private SharedBuffer buffer;
    private FireDroneGUI gui;
    private DroneSubsystem drone;
    private boolean running = true;

    //Iteration 2
    private Queue<FireEvent> fireQueue = new LinkedList<>();
    private DroneData droneInfo;

    public Scheduler(SharedBuffer buffer, FireDroneGUI gui, DroneSubsystem drone) {
        this.buffer = buffer;
        this.gui = gui;
        this.drone = drone;

        //initialize DroneInfo for drone 1
        this.droneInfo = new DroneData(drone.getDroneId());
    }

    @Override
    public void run() {
        gui.log("=== Scheduler STARTED ===");
        gui.log("[SCHEDULER] Managing Drone #" + droneInfo.getDroneId());
        gui.updateSchedulerStatus("IDLE");

        try {
            // Initial delay to let other threads initialize
            Thread.sleep(2000);

            // Main scheduling loop
            while (running) {

                //check for new fire events
                if (buffer.hasFireEvent()) {
                    FireEvent event = buffer.takeFireEvent();
                    fireQueue.add(event);
                    gui.log("===================================");
                    gui.log("[SCHEDULER] Fire queued: " + event);
                    gui.log("[SCHEDULER] Queue size: " + fireQueue.size());
                    gui.log("===================================");
                }

                //check for drone status updates
                if(buffer.hasDroneResponse()) {
                    DroneResponse response = buffer.takeDroneResponse();
                    handleDroneResponse(response);
                }

                //dispatch if possible
                if(shouldDispatch()) {
                    dispatchNextFire();
                }

                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            gui.log("Scheduler finished");
        }

        gui.log("=== Scheduler FINISHED ===");
    }

    //check if we should dispatch more drones
    public boolean shouldDispatch() {
        return !fireQueue.isEmpty() && hasAvailableDrone();
    }

    //check if any drone is available
    //Iteration 2: 1 drone
    public boolean hasAvailableDrone() {
        //Iteration 2
        return droneInfo.isAvailable();
    }

    private void reQueuePartialFire(DroneResponse response) {
        //get the mission that wasn't finished
        FireEvent unfinishedFire = droneInfo.getCurrentMission();
        if(unfinishedFire == null) return;

        // calculate remaining water needed
        double remainingWaterNeeded = unfinishedFire.getWaterNeeded() - response.getWaterUsed();
        if(remainingWaterNeeded <= 0.1) {
            gui.decrementActiveFires();
            return;
        }

        //calculate new severity
        String newSeverity = remainingWaterNeeded >= 25 ? "High" :
                remainingWaterNeeded >= 15 ? "Moderate" : "Low";

        //create new fire event with the reduced water needed
        FireEvent partialFire = new FireEvent(
                "Now",
                unfinishedFire.getZoneId(),
                "FIRE_DETECTED",
                newSeverity
        );

        // add to queue
        fireQueue.add(partialFire);

        //log it
        gui.log("[SCHEDULER] RE-QUEUED FIRE: Zone " + partialFire.getZoneId() + ", needs " + remainingWaterNeeded + "L more");

        //update the fire event gui
        gui.updateEventList("RE-QUEUED FIRE: Zone " + partialFire.getZoneId() + " (" + newSeverity + ", " + remainingWaterNeeded + "L)");

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