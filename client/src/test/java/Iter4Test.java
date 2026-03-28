import org.junit.jupiter.api.*;
import java.net.*;
import static org.junit.jupiter.api.Assertions.*;

public class Iter4Test {

    @Test
    void droneStuck_triggersFault() throws Exception {
        DatagramSocket receiver = new DatagramSocket(DroneSubsystem.PORT_SCHEDULER_DRONE);
        receiver.setSoTimeout(4000);

        new Thread(new DroneSubsystem(1, InetAddress.getLocalHost())).start();

        DatagramSocket sender = new DatagramSocket();

        // Send assignment with DRONE_STUCK
        send(sender, "1,10:00:00,1,FIRE,High,100,100,DRONE_STUCK");

        // Expect a single fault-related response
        boolean completed = false;

        try {
            while (true) {
                byte[] buf = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                receiver.receive(packet);

                String msg = new String(buf, 1, packet.getLength() - 1);

                if (msg.contains("COMPLETED")) {
                    completed = true;
                    break;
                }

                if (msg.contains("DRONE_STUCK") || msg.contains("FAULTED")) {
                    break;
                }
            }
        } catch (SocketTimeoutException ignored) {}

        assertFalse(completed, "Mission should not complete if drone is stuck");

        sender.close();
        receiver.close();
    }

    @Test
    void nozzleJammed_doesNotCompleteMission() throws Exception {
        DatagramSocket receiver = new DatagramSocket(DroneSubsystem.PORT_SCHEDULER_DRONE);
        receiver.setSoTimeout(4000);

        new Thread(new DroneSubsystem(1, InetAddress.getLocalHost())).start();

        DatagramSocket sender = new DatagramSocket();

        send(sender, "1,10:00:00,1,FIRE,High,100,100,NOZZLE_JAMMED");

        boolean completed = false;

        try {
            while (true) {
                byte[] buf = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                receiver.receive(packet);

                String msg = new String(buf, 1, packet.getLength() - 1);

                if (msg.contains("COMPLETED")) {
                    completed = true;
                    break;
                }

                if (msg.contains("NOZZLE_JAMMED") || msg.contains("FAULTED")) {
                    break;
                }
            }
        } catch (SocketTimeoutException ignored) {}

        assertFalse(completed, "Mission should not complete if nozzle is jammed");

        sender.close();
        receiver.close();
    }

    private void send(DatagramSocket socket, String payload) throws Exception {
        byte[] data = new byte[payload.length() + 1];
        data[0] = 0x02; // assignment
        System.arraycopy(payload.getBytes(), 0, data, 1, payload.length());

        socket.send(new DatagramPacket(
                data,
                data.length,
                InetAddress.getLocalHost(),
                DroneSubsystem.PORT_DRONE_BASE + 1
        ));
    }
}