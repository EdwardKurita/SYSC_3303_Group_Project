import java.io.File;
import java.net.InetAddress;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("==========================================");
        System.out.println(" FIRE INCIDENT SUBSYSTEM  (Server)");
        System.out.println("==========================================");

        InetAddress schedulerAddress = InetAddress.getLocalHost();

        String base = System.getProperty("user.dir");
        String eventsPath = base + File.separator + "data" + File.separator + "events.csv";
        String zonesPath = base + File.separator + "data" + File.separator + "zones.csv";

        if (!new File(eventsPath).exists() || !new File(zonesPath).exists()) {
            System.err.println("ERROR: data/events.csv or data/zones.csv not found.");
            System.err.println("Expected at: " + base + File.separator + "data" + File.separator);
            return;
        }

        List<FireIncidentZone> zones = FireIncidentSubsystem.loadZonesForGUI(zonesPath);

        FireDroneGUI gui = new FireDroneGUI(zones);
        gui.log("Fire Incident Subsystem starting...");
        gui.log("Scheduler address: " + schedulerAddress.getHostAddress());
        gui.log("Listening for GUI updates on port " + FireIncidentSubsystem.PORT_FIRE_SERVER);

        Thread guiThread = new Thread(new GuiUpdateReceiver(gui), "GUI-Receiver");
        guiThread.start();

        Thread fireThread = new Thread(new FireIncidentSubsystem(gui, eventsPath, zonesPath, schedulerAddress), "FireIncident");
        fireThread.start();
        fireThread.join();

        // Keep GUI alive so the user can observe drone movements
        gui.log("All fire events sent. GUI remains open for live drone updates.");
        System.out.println("All fire events dispatched. GUI staying open...");
    }
}