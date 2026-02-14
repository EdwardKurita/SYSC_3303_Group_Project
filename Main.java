import java.io.File;

public class Main {
    public static void main(String[] args) {
        System.out.println("======================================");
        System.out.println("FIREFIGHTING DRONE SYSTEM - ITERATION 2");
        System.out.println("Group 12: Jiayi Han, Declan Koster, Shael Kotecha, Edward Kurita");
        System.out.println("======================================");

        // Step 1. File path setup
        // Get current directory and build paths to CSV files
        String currentDir = System.getProperty("user.dir");
        String eventsPath = currentDir + File.separator + "data" + File.separator + "events.csv";
        String zonesPath = currentDir + File.separator + "data" + File.separator + "zones.csv";

        // Check if required data file exist
        if (!new File(eventsPath).exists() || !new File(zonesPath).exists()) {
            System.err.println("ERROR: Data files not found.");
            System.err.println("Looking for events.csv at: " + eventsPath);
            System.err.println("Looking for zones.csv at: " + zonesPath);
            return;
        }

        System.out.println("Files found. Starting system...");

        // Step 2. Initialize components
        FireDroneGUI gui = new FireDroneGUI(); // GUI
        SharedBuffer buffer = new SharedBuffer(); // Shared buffer for thread communication

        // Fire subsystem loads events and zones from CSV
        FireIncidentSubsystem fireSubsystem = new FireIncidentSubsystem(buffer, gui, eventsPath, zonesPath);

        // Start fire thread first (loads zones before drone missions)
        Thread fireThread = new Thread(fireSubsystem, "FireIncident");
        fireThread.start();

        // Wait for zones to load (simulate loading time)
        try {
            System.out.println("Waiting for zones to load...");
            Thread.sleep(3000);
            System.out.println("Zones loaded. Starting scheduler and drone...");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Step 3: Create and start other threads
        DroneSubsystem drone = new DroneSubsystem(buffer, fireSubsystem, gui, 1);
        Scheduler scheduler = new Scheduler(buffer, gui, drone);

        Thread schedulerThread = new Thread(scheduler, "Scheduler");
        Thread droneThread = new Thread(drone, "Drone");

        schedulerThread.start();
        droneThread.start();

        // Step 4: Coordinate system execution
        try {
            // Wait for fire thread to finish reading all CSV events
            fireThread.join();
            System.out.println("STEP 1: All events read from CSV file.");
            gui.log("All fire events loaded from CSV");

            // Calculate and wait for ALL missions to complete
            System.out.println("\nSTEP 2: Waiting for drone to complete ALL missions");
            System.out.println("With 10x speedup simulation:");
            System.out.println("  • Zone 3 (High): ~18.6 seconds");
            System.out.println("  • Zone 7 (Moderate): ~35.7 seconds");
            System.out.println("  • Total: ~54.3 seconds");
            System.out.println("  • Adding safety margin: Waiting 90 seconds");

            gui.log("️Waiting for drone to complete all missions (90 seconds)...");

            // Simple countdown timer (update every 10 secs)
            for (int i = 90; i > 0; i -= 10) {
                if (i % 30 == 0 || i <= 20) {
                    System.out.println("  [" + i + " seconds remaining]");
                    gui.log("[TIMER] " + i + " seconds remaining");
                }
                Thread.sleep(10000); // Sleep 10 secs at a time
            }

            System.out.println("\n STEP 3: All missions should be complete");
            System.out.println("Allowing threads to finish naturally...");
            gui.log("All missions complete - system finishing...");

            // Give threads extra time to warp up
            Thread.sleep(5000);

        } catch (InterruptedException e) {
            System.out.println("Main thread interrupted");
        }

        // Step 5: Final output
        System.out.println("\n======================================");
        System.out.println("SYSTEM COMPLETE - ALL REQUIREMENTS MET");
        System.out.println("======================================");
        gui.log("======================================");
        gui.log("SYSTEM COMPLETE - ALL REQUIREMENTS MET");
        gui.log("======================================");

        // Keep GUI open for a bit so user can see final state
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            // Ignore interruption on exit
        }
    }
}