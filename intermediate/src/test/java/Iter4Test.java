import org.junit.jupiter.api.*;
import java.net.*;
import static org.junit.jupiter.api.Assertions.*;

public class Iter4Test {

    /**
     * this test checks that a fire gets requeued after a drone gets stuck. If the drone gets stuck
     * the fire should get requeued and this teste simply checks if that assignment goes back in queue.
     *
     */
    @Test
    void droneStuck_requeuesFireEvent() throws Exception {
        try (DatagramSocket droneReceiver = new DatagramSocket(Scheduler.PORT_DRONE_BASE + 1)) {
            droneReceiver.setSoTimeout(3000);

            Scheduler scheduler = new Scheduler(InetAddress.getLocalHost());
            Thread thread = new Thread(scheduler);
            thread.start();

            DatagramSocket sender = new DatagramSocket();

            // Register drone as active
            String status = "1,1,EN_ROUTE,Working,0,15,100,100,NONE";
            send(sender, status, 0x03, Scheduler.PORT_SCHEDULER_DRONE);

            // Send fire event
            String fire = "10:00:00,1,FIRE_DETECTED,High,DRONE_STUCK";
            send(sender, fire, 0x01, Scheduler.PORT_SCHEDULER_FIRE);

            Thread.sleep(500);

            // Fault the drone
            String fault = "1,1,DRONE_STUCK,Stuck,0,15,100,100,DRONE_STUCK";
            send(sender, fault, 0x03, Scheduler.PORT_SCHEDULER_DRONE);

            // used to check and see if the drone captured the assignment again
            boolean gotAssignment = true;

            // try to catch the packet being reassigned to the drone
            try {
                byte[] buf = new byte[1024];
                droneReceiver.receive(new DatagramPacket(buf, buf.length));
            } catch (SocketTimeoutException e) {
                // in this case, the scheduler doens't create the packet, which is what we want (drone is not
                // available).
                gotAssignment = false;
            }

            assertFalse(gotAssignment, "Fire event should be requeued, not reassigned to faulted drone");

            scheduler.running = false;
            thread.join();
            sender.close();
        }
    }

    // same thing as the previous test here, but with nozzle jammed instead of stuck.

    @Test
    void nozzleJammed_requeuesFireEvent() throws Exception {
        try (DatagramSocket droneReceiver = new DatagramSocket(Scheduler.PORT_DRONE_BASE + 1)) {
            droneReceiver.setSoTimeout(3000);

            Scheduler scheduler = new Scheduler(InetAddress.getLocalHost());
            Thread thread = new Thread(scheduler);
            thread.start();

            DatagramSocket sender = new DatagramSocket();

            // Send fire event
            String fire = "10:00:00,1,FIRE_DETECTED,High,NOZZLE_JAMMED";
            send(sender, fire, 0x01, Scheduler.PORT_SCHEDULER_FIRE);

            Thread.sleep(500);

            // Fault drone
            String fault = "1,1,FAULTED,Nozzle jammed,0,15,100,100,NOZZLE_JAMMED";
            send(sender, fault, 0x03, Scheduler.PORT_SCHEDULER_DRONE);

            boolean gotAssignment = true;
            try {
                byte[] buf = new byte[1024];
                droneReceiver.receive(new DatagramPacket(buf, buf.length));
            } catch (SocketTimeoutException e) {
                gotAssignment = false;
            }

            assertFalse(gotAssignment, "Hard fault should remove drone and requeue mission");

            scheduler.running = false;
            thread.join();
            sender.close();
        }
    }

    // helper
    private void send(DatagramSocket socket, String payload, int type, int port) throws Exception {
        byte[] data = new byte[payload.length() + 1];
        data[0] = (byte) type;
        System.arraycopy(payload.getBytes(), 0, data, 1, payload.length());

        socket.send(new DatagramPacket(
                data,
                data.length,
                InetAddress.getLocalHost(),
                port
        ));
    }
}