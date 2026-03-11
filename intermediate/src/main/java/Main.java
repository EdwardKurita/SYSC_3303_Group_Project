import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Main – Scheduler (Intermediate Host)
 *
 * Usage:
 *   java -cp out Main [fireServerHost] [maxDrones] [zonesCSVPath]
 *
 *   fireServerHost  IP of the FireIncidentSubsystem machine  (default: localhost)
 *   maxDrones       How many drone clients to expect         (default: 3)
 *   zonesCSVPath    Path to zones.csv                        (default: ./data/zones.csv)
 */
public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("==========================================");
        System.out.println(" SCHEDULER  (Intermediate Host)");
        System.out.println("==========================================");

        String fireHost  = (args.length > 0) ? args[0] : "localhost";
        int    maxDrones = (args.length > 1) ? Integer.parseInt(args[1]) : 3;
        String zonesPath = (args.length > 2) ? args[2]
                : System.getProperty("user.dir") + File.separator + "data"
                + File.separator + "zones.csv";

        InetAddress fireAddress = InetAddress.getByName(fireHost);
        System.out.println("Fire server address : " + fireAddress.getHostAddress());
        System.out.println("Max drones          : " + maxDrones);
        System.out.println("Zones CSV           : " + zonesPath);

        // Load zone bounds and centres from zones.csv
        Map<Integer, double[]> zoneBounds  = new LinkedHashMap<>();
        Map<Integer, double[]> zoneCentres = new LinkedHashMap<>();
        loadZones(zonesPath, zoneBounds, zoneCentres);

        System.out.println("Listening on ports  : "
                + Scheduler.PORT_SCHEDULER_FIRE + " (fire events), "
                + Scheduler.PORT_SCHEDULER_DRONE + " (drone status)");

        new Scheduler(fireAddress, maxDrones, zoneBounds, zoneCentres).run();
    }

    /**
     * Parses zones.csv and fills both maps.
     * zoneBounds  : zoneId -> [x1, y1, x2, y2]
     * zoneCentres : zoneId -> [cx, cy]
     */
    private static void loadZones(String zonesPath,
                                  Map<Integer, double[]> zoneBounds,
                                  Map<Integer, double[]> zoneCentres) {
        File f = new File(zonesPath);
        if (!f.exists()) {
            System.err.println("WARNING: zones.csv not found at " + zonesPath
                    + " – passthrough detection disabled.");
            return;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                int id = Integer.parseInt(parts[0].trim());
                String[] sc = parts[1].trim().replace("(","").replace(")","").split(";");
                String[] ec = parts[2].trim().replace("(","").replace(")","").split(";");
                double x1 = Integer.parseInt(sc[0]), y1 = Integer.parseInt(sc[1]);
                double x2 = Integer.parseInt(ec[0]), y2 = Integer.parseInt(ec[1]);
                zoneBounds.put(id,  new double[]{ x1, y1, x2, y2 });
                zoneCentres.put(id, new double[]{ (x1 + x2) / 2.0, (y1 + y2) / 2.0 });
                System.out.printf("  Zone %2d  bounds=(%.0f,%.0f)-(%.0f,%.0f)  centre=(%.0f,%.0f)%n",
                        id, x1, y1, x2, y2, (x1+x2)/2.0, (y1+y2)/2.0);
            }
        } catch (IOException e) {
            System.err.println("ERROR reading zones.csv: " + e.getMessage());
        }
    }
}