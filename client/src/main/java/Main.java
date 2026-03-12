import java.net.InetAddress;

public class Main {
    public static void main(String[] args) throws Exception {
        args = new String[2];
        args[0] = "1";
        if (args.length < 1) {
            System.err.println("Usage: java -cp out Main <droneId> [schedulerHost]");
            System.exit(1);
        }

        int droneId = Integer.parseInt(args[0]);
        String schedulerHost = (args.length > 1) ? args[1] : "localhost";
        InetAddress schedulerAddr = InetAddress.getByName(schedulerHost);

        System.out.println("==========================================");
        System.out.println(" DRONE CLIENT  #" + droneId);
        System.out.println("==========================================");
        System.out.println("Scheduler address : " + schedulerAddr.getHostAddress());
        System.out.println("Listen port       : " + (DroneSubsystem.PORT_DRONE_BASE + droneId));
        System.out.println("Sending status to : port " + DroneSubsystem.PORT_SCHEDULER_DRONE);

        new DroneSubsystem(droneId, schedulerAddr).run();
    }
}