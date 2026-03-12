import java.net.*;

public class GuiUpdateReceiver implements Runnable {
    private static final byte TYPE_GUI_UPDATE = 0x04;
    private static final byte TYPE_SHUTDOWN   = 0x0F;

    private final FireDroneGUI gui;
    private volatile boolean running = true;

    public GuiUpdateReceiver(FireDroneGUI gui) {
        this.gui = gui;
    }

    @Override
    public void run() {
        gui.log("[GUI] Listening on port " + FireIncidentSubsystem.PORT_FIRE_SERVER);
        try (DatagramSocket schedulerSocket = new DatagramSocket(FireIncidentSubsystem.PORT_FIRE_SERVER)) {
            while (running) {
                byte[] data = new byte[1024];
                DatagramPacket packet = new DatagramPacket(data, data.length);
                schedulerSocket.receive(packet);

                int len = packet.getLength();

                if (data[0] == TYPE_GUI_UPDATE) {
                    // fields: [droneId, status, posX, posY, waterRemaining, zoneId, severity]
                    String[] parsed = new String(data, 0, len).split(",");

                    int droneId = Integer.parseInt(parsed[0]);
                    String status = parsed[1];
                    double posX = Double.parseDouble(parsed[2]);
                    double posY = Double.parseDouble(parsed[3]);
                    double waterRemaining = Double.parseDouble(parsed[4]);
                    int zoneId = Integer.parseInt(parsed[5]);
                    String severity = (parsed.length > 6) ? parsed[6] : "NONE";

                    gui.updateDroneMarker(droneId, status, posX, posY, waterRemaining, zoneId, severity);

                } else if (data[0] == TYPE_SHUTDOWN) {
                    gui.log("[GUI] Shutdown received.");
                    running = false;
                }
            }
        } catch (Exception e) {
            if (running) {
                gui.logError("[GUI] " + e.getMessage());
            }
        }
        gui.log("[GUI] Stopped.");
    }
}
