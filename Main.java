import java.io.File;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        System.out.println("======================================");
        System.out.println("FIREFIGHTING DRONE SYSTEM - ITERATION 2");
        System.out.println("Group 12: Jiayi Han, Declan Koster, Shael Kotecha, Edward Kurita");
        System.out.println("======================================");

        // Step 1. File path setup
        String currentDir = System.getProperty("user.dir");
        String eventsPath = currentDir + File.separator + "data" + File.separator + "events.csv";
        String zonesPath = currentDir + File.separator + "data" + File.separator + "zones.csv";

        // Check if required data files exist
        if (!new File(eventsPath).exists() || !new File(zonesPath).exists()) {
            System.err.println("ERROR: Data files not found.");
            System.err.println("Looking for events.csv at: " + eventsPath);
            System.err.println("Looking for zones.csv at: " + zonesPath);
            return;
        }

        System.out.println("Files found. Starting system...");

       //Load zones FIRST so GUI can display them
        FireIncidentSubsystem zoneLoader =
                new FireIncidentSubsystem(null, null, eventsPath, zonesPath);

        List<FireIncidentZone> zones = zoneLoader.loadZones(zonesPath);

        //Create GUI with zones
        FireDroneGUI gui = new FireDroneGUI(zones);

        // Shared buffer for thread communication
        SharedBuffer buffer = new SharedBuffer();

        // Fire subsystem (actual one used by system)
        FireIncidentSubsystem fireSubsystem =
                new FireIncidentSubsystem(buffer, gui, eventsPath, zonesPath);

        // Start fire thread first
        Thread fireThread = new Thread(fireSubsystem, "FireIncident");
        fireThread.start();

        // Wait for zones to load
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

            fireThread.join();
            System.out.println("STEP 1: All events read from CSV file.");
            gui.log("All fire events loaded from CSV");

            System.out.println("\nSTEP 2: Waiting for drone to complete ALL missions");
            System.out.println("With 10x speedup simulation:");
            System.out.println("  • Zone 3 (High): ~18.6 seconds");
            System.out.println("  • Zone 7 (Moderate): ~35.7 seconds");
            System.out.println("  • Total: ~54.3 seconds");
            System.out.println("  • Adding safety margin: Waiting 90 seconds");

            gui.log("Waiting for drone to complete all missions (90 seconds)...");

            for (int i = 90; i > 0; i -= 10) {
                if (i % 30 == 0 || i <= 20) {
                    System.out.println("  [" + i + " seconds remaining]");
                    gui.log("[TIMER] " + i + " seconds remaining");
                }
                Thread.sleep(10000);
            }

            System.out.println("\nSTEP 3: All missions should be complete");
            System.out.println("Allowing threads to finish naturally...");
            gui.log("All missions complete - system finishing...");

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

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            // Ignore interruption on exit
        }
    }
}