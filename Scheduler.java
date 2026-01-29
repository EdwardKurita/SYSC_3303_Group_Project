// Schedular coordinates between FireIncidentSubsystem and DroneSubsystem
// It reads fire events from the buffer and assigns them to the drone, then listens fpr drone responses and updates the GUI
public class Scheduler implements Runnable {
    private SharedBuffer buffer;
    private FireDroneGUI gui;
    private DroneSubsystem drone;
    private boolean running = true;

    public Scheduler(SharedBuffer buffer, FireDroneGUI gui, DroneSubsystem drone) {
        this.buffer = buffer;
        this.gui = gui;
        this.drone = drone;
    }

    @Override
    public void run() {
        gui.log("=== Scheduler STARTED ===");
        gui.updateSchedulerStatus("Ready");

        try {
            // Initial delay to let other threads initialize
            Thread.sleep(2000);

            // Main shceduling loop
            while (running) {
                // Process fire events
                if (buffer.hasFireEvent()) {
                    FireEvent event = buffer.takeFireEvent();
                    gui.log("[SCHEDULER] Received: " + event);
                    gui.updateSchedulerStatus("Processing Zone " + event.getZoneId());

                    // Assign event to drone for processing
                    gui.log("[SCHEDULER] Assigning to drone...");

                    try {
                        drone.processAssignedFire(event);
                    } catch (Exception e) {
                        gui.logError("[SCHEDULER] Could not process fire: " + e.getMessage());
                    }
                }

                // Process drone responses
                if (buffer.hasDroneResponse()) {
                    DroneResponse response = buffer.takeDroneResponse();
                    gui.log("[SCHEDULER] Drone: " + response);
                    gui.updateDroneStatus(response); // Update GUI with drone state
                }

                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            gui.log("Scheduler finished");
        }

        gui.log("=== Scheduler FINISHED ===");
    }
}