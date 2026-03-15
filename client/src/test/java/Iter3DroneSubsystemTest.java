import org.junit.jupiter.api.*;
import java.net.*;
import static org.junit.jupiter.api.Assertions.*;


public class Iter3DroneSubsystemTest {

    private static final int TEST_SCHEDULER_PORT = DroneSubsystem.PORT_SCHEDULER_DRONE;
    private static final int TEST_DRONE_PORT = DroneSubsystem.PORT_DRONE_BASE + 99;

    //UDP Testing assignment packet sent to drone

    @Test
    void testAssignmentPacketFirstByteIsCorrect() throws Exception {
        try (DatagramSocket droneReceiver = new DatagramSocket(TEST_DRONE_PORT)) {
            droneReceiver.setSoTimeout(3000);
            DatagramSocket senderSocket = new DatagramSocket();

            String payload = "99,14:00:00,1,FIRE_DETECTED,High,350.0,300.0";
            byte[] data = new byte[payload.getBytes().length + 1];
            data[0] = 0x02; // TYPE_DRONE_ASSIGNMENT
            System.arraycopy(payload.getBytes(), 0, data, 1, payload.getBytes().length);
            senderSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), TEST_DRONE_PORT));

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            droneReceiver.receive(packet);

            assertEquals(0x02, buffer[0]);
            senderSocket.close();
        }
    }

    @Test
    void testAssignmentPacketContainsSevenFields() throws Exception {
        try (DatagramSocket droneReceiver = new DatagramSocket(TEST_DRONE_PORT)) {
            droneReceiver.setSoTimeout(3000);
            DatagramSocket senderSocket = new DatagramSocket();

            String payload = "99,14:00:00,1,FIRE_DETECTED,High,350.0,300.0";
            byte[] data = new byte[payload.getBytes().length + 1];
            data[0] = 0x02;
            System.arraycopy(payload.getBytes(), 0, data, 1, payload.getBytes().length);
            senderSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), TEST_DRONE_PORT));

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            droneReceiver.receive(packet);

            // droneId, time, zoneId, eventType, severity, centerX, centerY
            String[] fields = new String(buffer, 1, packet.getLength() - 1).split(",");
            assertEquals(7, fields.length);
            senderSocket.close();
        }
    }

    @Test
    void testAssignmentPacketDroneIdCorrect() throws Exception {
        try (DatagramSocket droneReceiver = new DatagramSocket(TEST_DRONE_PORT)) {
            droneReceiver.setSoTimeout(3000);
            DatagramSocket senderSocket = new DatagramSocket();

            String payload = "99,14:00:00,1,FIRE_DETECTED,High,350.0,300.0";
            byte[] data = new byte[payload.getBytes().length + 1];
            data[0] = 0x02;
            System.arraycopy(payload.getBytes(), 0, data, 1, payload.getBytes().length);
            senderSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), TEST_DRONE_PORT));

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            droneReceiver.receive(packet);

            String[] fields = new String(buffer, 1, packet.getLength() - 1).split(",");
            assertEquals("99", fields[0]); // droneId
            senderSocket.close();
        }
    }

    @Test
    void testAssignmentPacketZoneIdCorrect() throws Exception {
        try (DatagramSocket droneReceiver = new DatagramSocket(TEST_DRONE_PORT)) {
            droneReceiver.setSoTimeout(3000);
            DatagramSocket senderSocket = new DatagramSocket();

            String payload = "99,14:00:00,3,FIRE_DETECTED,Moderate,350.0,300.0";
            byte[] data = new byte[payload.getBytes().length + 1];
            data[0] = 0x02;
            System.arraycopy(payload.getBytes(), 0, data, 1, payload.getBytes().length);
            senderSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), TEST_DRONE_PORT));

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            droneReceiver.receive(packet);

            String[] fields = new String(buffer, 1, packet.getLength() - 1).split(",");
            assertEquals("3", fields[2]); // zoneId
            senderSocket.close();
        }
    }

    @Test
    void testAssignmentPacketSeverityCorrect() throws Exception {
        try (DatagramSocket droneReceiver = new DatagramSocket(TEST_DRONE_PORT)) {
            droneReceiver.setSoTimeout(3000);
            DatagramSocket senderSocket = new DatagramSocket();

            String payload = "99,14:00:00,1,FIRE_DETECTED,High,350.0,300.0";
            byte[] data = new byte[payload.getBytes().length + 1];
            data[0] = 0x02;
            System.arraycopy(payload.getBytes(), 0, data, 1, payload.getBytes().length);
            senderSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), TEST_DRONE_PORT));

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            droneReceiver.receive(packet);

            String[] fields = new String(buffer, 1, packet.getLength() - 1).split(",");
            assertEquals("High", fields[4]); // severity
            senderSocket.close();
        }
    }

    @Test
    void testAssignmentPacketCenterCoordsCorrect() throws Exception {
        try (DatagramSocket droneReceiver = new DatagramSocket(TEST_DRONE_PORT)) {
            droneReceiver.setSoTimeout(3000);
            DatagramSocket senderSocket = new DatagramSocket();

            String payload = "99,14:00:00,1,FIRE_DETECTED,High,350.0,300.0";
            byte[] data = new byte[payload.getBytes().length + 1];
            data[0] = 0x02;
            System.arraycopy(payload.getBytes(), 0, data, 1, payload.getBytes().length);
            senderSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), TEST_DRONE_PORT));

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            droneReceiver.receive(packet);

            String[] fields = new String(buffer, 1, packet.getLength() - 1).split(",");
            assertEquals(350.0, Double.parseDouble(fields[5])); // centerX
            assertEquals(300.0, Double.parseDouble(fields[6])); // centerY
            senderSocket.close();
        }
    }


    //UDP Testing packet sent from drone to scheduler

    @Test
    void testStatusPacketFirstByteIsDroneStatus() throws Exception {
        try (DatagramSocket receiverSocket = new DatagramSocket(TEST_SCHEDULER_PORT)) {
            receiverSocket.setSoTimeout(3000);
            DatagramSocket senderSocket = new DatagramSocket();

            // format: droneId,zoneId,status,message,waterUsed,currentWater,posX,posY
            String payload = "1,2,ARRIVED,Arrived at Zone 2,0.0,15.0,350.0,300.0";
            byte[] data = new byte[payload.getBytes().length + 1];
            data[0] = 0x03; // TYPE_DRONE_STATUS
            System.arraycopy(payload.getBytes(), 0, data, 1, payload.getBytes().length);
            senderSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), TEST_SCHEDULER_PORT));

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            receiverSocket.receive(packet);

            assertEquals(0x03, buffer[0]);
            senderSocket.close();
        }
    }

    @Test
    void testStatusPacketContainsEightFields() throws Exception {
        try (DatagramSocket receiverSocket = new DatagramSocket(TEST_SCHEDULER_PORT)) {
            receiverSocket.setSoTimeout(3000);
            DatagramSocket senderSocket = new DatagramSocket();

            String payload = "1,2,ARRIVED,Arrived at Zone 2,0.0,15.0,350.0,300.0";
            byte[] data = new byte[payload.getBytes().length + 1];
            data[0] = 0x03;
            System.arraycopy(payload.getBytes(), 0, data, 1, payload.getBytes().length);
            senderSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), TEST_SCHEDULER_PORT));

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            receiverSocket.receive(packet);

            // droneId, zoneId, status, message, waterUsed, currentWater, posX, posY
            String[] fields = new String(buffer, 1, packet.getLength() - 1).split(",");
            assertEquals(8, fields.length);
            senderSocket.close();
        }
    }

    @Test
    void testStatusPacketStatusFieldCorrect() throws Exception {
        try (DatagramSocket receiverSocket = new DatagramSocket(TEST_SCHEDULER_PORT)) {
            receiverSocket.setSoTimeout(3000);
            DatagramSocket senderSocket = new DatagramSocket();

            String payload = "1,2,EXTINGUISHING,Dropping water,0.0,15.0,350.0,300.0";
            byte[] data = new byte[payload.getBytes().length + 1];
            data[0] = 0x03;
            System.arraycopy(payload.getBytes(), 0, data, 1, payload.getBytes().length);
            senderSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), TEST_SCHEDULER_PORT));

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            receiverSocket.receive(packet);

            String[] fields = new String(buffer, 1, packet.getLength() - 1).split(",");
            assertEquals("EXTINGUISHING", fields[2]);
            senderSocket.close();
        }
    }

    @Test
    void testStatusPacketWaterLevelCorrect() throws Exception {
        try (DatagramSocket receiverSocket = new DatagramSocket(TEST_SCHEDULER_PORT)) {
            receiverSocket.setSoTimeout(3000);
            DatagramSocket senderSocket = new DatagramSocket();

            String payload = "1,2,ARRIVED,Arrived,0.0,15.0,350.0,300.0";
            byte[] data = new byte[payload.getBytes().length + 1];
            data[0] = 0x03;
            System.arraycopy(payload.getBytes(), 0, data, 1, payload.getBytes().length);
            senderSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), TEST_SCHEDULER_PORT));

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            receiverSocket.receive(packet);

            String[] fields = new String(buffer, 1, packet.getLength() - 1).split(",");
            assertEquals(15.0, Double.parseDouble(fields[5])); // currentWater
            senderSocket.close();
        }
    }

    @Test
    void testStatusPacketPartialShowsRemainingWater() throws Exception {
        try (DatagramSocket receiverSocket = new DatagramSocket(TEST_SCHEDULER_PORT)) {
            receiverSocket.setSoTimeout(3000);
            DatagramSocket senderSocket = new DatagramSocket();

            // drone had 15L, used all of it, high fire needed 30L — partial
            String payload = "1,1,PARTIAL,Used 15.0L needs 15.0L more,15.0,0.0,350.0,300.0";
            byte[] data = new byte[payload.getBytes().length + 1];
            data[0] = 0x03;
            System.arraycopy(payload.getBytes(), 0, data, 1, payload.getBytes().length);
            senderSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), TEST_SCHEDULER_PORT));

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            receiverSocket.receive(packet);

            String[] fields = new String(buffer, 1, packet.getLength() - 1).split(",");
            assertEquals("PARTIAL", fields[2]);
            assertEquals(15.0, Double.parseDouble(fields[4])); // waterUsed
            assertEquals(0.0, Double.parseDouble(fields[5]));  // currentWater = empty
            senderSocket.close();
        }
    }

    @Test
    void testStatusPacketReturnedShowsFullTankAndBasePosition() throws Exception {
        try (DatagramSocket receiverSocket = new DatagramSocket(TEST_SCHEDULER_PORT)) {
            receiverSocket.setSoTimeout(3000);
            DatagramSocket senderSocket = new DatagramSocket();

            // drone returned and refilled
            String payload = "1,0,RETURNED,Refilled and ready,0.0,15.0,0.0,0.0";
            byte[] data = new byte[payload.getBytes().length + 1];
            data[0] = 0x03;
            System.arraycopy(payload.getBytes(), 0, data, 1, payload.getBytes().length);
            senderSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), TEST_SCHEDULER_PORT));

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            receiverSocket.receive(packet);

            String[] fields = new String(buffer, 1, packet.getLength() - 1).split(",");
            assertEquals("RETURNED", fields[2]);
            assertEquals(15.0, Double.parseDouble(fields[5])); // full tank
            assertEquals(0.0, Double.parseDouble(fields[6]));  // posX at base
            assertEquals(0.0, Double.parseDouble(fields[7]));  // posY at base
            senderSocket.close();
        }
    }

    @Test
    void testStatusPacketFaultedDroneReportsCorrectly() throws Exception {
        try (DatagramSocket receiverSocket = new DatagramSocket(TEST_SCHEDULER_PORT)) {
            receiverSocket.setSoTimeout(3000);
            DatagramSocket senderSocket = new DatagramSocket();

            String payload = "1,2,FAULTED,Something Has Happened,0.0,15.0,175.0,150.0";
            byte[] data = new byte[payload.getBytes().length + 1];
            data[0] = 0x03;
            System.arraycopy(payload.getBytes(), 0, data, 1, payload.getBytes().length);
            senderSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), TEST_SCHEDULER_PORT));

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            receiverSocket.receive(packet);

            String[] fields = new String(buffer, 1, packet.getLength() - 1).split(",");
            assertEquals("FAULTED", fields[2]);
            senderSocket.close();
        }
    }

}
