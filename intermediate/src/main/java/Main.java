import java.net.*;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("==========================================");
        System.out.println(" SCHEDULER  (Intermediate Host)");
        System.out.println("==========================================");

        InetAddress fireAddress = InetAddress.getLocalHost();

        System.out.println("Fire server address : " + fireAddress.getHostAddress());
        System.out.println("Listening on ports  : " + Scheduler.PORT_SCHEDULER_FIRE  + " (FireEventSubsystem), " + Scheduler.PORT_SCHEDULER_DRONE + " (Drones)");

        Scheduler scheduler = new Scheduler(fireAddress);

        //shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[SHUTDOWN] Printing final metrics:");
            scheduler.printFinalMetrics();
        }, "shutdownMetrics"));

        scheduler.run();
    }
}
