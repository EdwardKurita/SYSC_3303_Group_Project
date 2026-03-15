import org.junit.jupiter.api.*;
import java.io.*;
import java.net.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class Iter3FireIncidentSubsystemTest {

    //UDP zone packets sent From FireIncidentSubsystem to scheduler

    @Test
    void testZonePacketFirstByteIsZoneData() throws Exception {
        try (DatagramSocket receiverSocket = new DatagramSocket(FireIncidentSubsystem.PORT_SCHEDULER_FIRE)) {
            receiverSocket.setSoTimeout(3000);
            DatagramSocket senderSocket = new DatagramSocket();

            //build a zone packet the same way sendZones() does
            //format: zoneId,x1,y1,x2,y2
            String payload = "1,0,0,700,600";
            byte[] data = new byte[payload.getBytes().length + 1];
            data[0] = 0x00; // TYPE_ZONE_DATA
            System.arraycopy(payload.getBytes(), 0, data, 1, payload.getBytes().length);
            senderSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), FireIncidentSubsystem.PORT_SCHEDULER_FIRE));

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            receiverSocket.receive(packet);

            assertEquals(0x00, buffer[0]);
            senderSocket.close();
        }
    }

    @Test
    void testZonePacketZoneIdCorrect() throws Exception {
        try (DatagramSocket receiverSocket = new DatagramSocket(FireIncidentSubsystem.PORT_SCHEDULER_FIRE)) {
            receiverSocket.setSoTimeout(3000);
            DatagramSocket senderSocket = new DatagramSocket();

            String payload = "2,0,600,650,1500";
            byte[] data = new byte[payload.getBytes().length + 1];
            data[0] = 0x00;
            System.arraycopy(payload.getBytes(), 0, data, 1, payload.getBytes().length);
            senderSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), FireIncidentSubsystem.PORT_SCHEDULER_FIRE));

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            receiverSocket.receive(packet);

            String[] fields = new String(buffer, 1, packet.getLength() - 1).split(",");
            assertEquals("2", fields[0]); // zoneId
            senderSocket.close();
        }
    }

    @Test
    void testZonePacketBoundsCorrect() throws Exception {
        try (DatagramSocket receiverSocket = new DatagramSocket(FireIncidentSubsystem.PORT_SCHEDULER_FIRE)) {
            receiverSocket.setSoTimeout(3000);
            DatagramSocket senderSocket = new DatagramSocket();

            String payload = "1,0,0,700,600";
            byte[] data = new byte[payload.getBytes().length + 1];
            data[0] = 0x00;
            System.arraycopy(payload.getBytes(), 0, data, 1, payload.getBytes().length);
            senderSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), FireIncidentSubsystem.PORT_SCHEDULER_FIRE));

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            receiverSocket.receive(packet);

            String[] fields = new String(buffer, 1, packet.getLength() - 1).split(",");
            assertEquals(0.0, Double.parseDouble(fields[1])); // x1
            assertEquals(0.0, Double.parseDouble(fields[2])); // y1
            assertEquals(700.0, Double.parseDouble(fields[3])); // x2
            assertEquals(600.0, Double.parseDouble(fields[4])); // y2
            senderSocket.close();
        }
    }

    //UDP fireEvent packet sent from FireIncidentSubsystem to scheduler

    @Test
    void testFireEventPacketFirstByteIsFireEvent() throws Exception {
        try (DatagramSocket receiverSocket = new DatagramSocket(FireIncidentSubsystem.PORT_SCHEDULER_FIRE)) {
            receiverSocket.setSoTimeout(3000);
            DatagramSocket senderSocket = new DatagramSocket();

            //build a fire event packet the same way sendFireEvents() does
            //format: time,zoneId,eventType,severity
            String payload = "14:03:15,1,FIRE_DETECTED,High";
            byte[] data = new byte[payload.getBytes().length + 1];
            data[0] = 0x01; // TYPE_FIRE_EVENT
            System.arraycopy(payload.getBytes(), 0, data, 1, payload.getBytes().length);
            senderSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), FireIncidentSubsystem.PORT_SCHEDULER_FIRE));

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            receiverSocket.receive(packet);

            assertEquals(0x01, buffer[0]);
            senderSocket.close();
        }
    }

    @Test
    void testFireEventPacketContainsFourFields() throws Exception {
        try (DatagramSocket receiverSocket = new DatagramSocket(FireIncidentSubsystem.PORT_SCHEDULER_FIRE)) {
            receiverSocket.setSoTimeout(3000);
            DatagramSocket senderSocket = new DatagramSocket();

            String payload = "14:03:15,1,FIRE_DETECTED,High";
            byte[] data = new byte[payload.getBytes().length + 1];
            data[0] = 0x01;
            System.arraycopy(payload.getBytes(), 0, data, 1, payload.getBytes().length);
            senderSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), FireIncidentSubsystem.PORT_SCHEDULER_FIRE));

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            receiverSocket.receive(packet);

            // time, zoneId, eventType, severity
            String[] fields = new String(buffer, 1, packet.getLength() - 1).split(",");
            assertEquals(4, fields.length);
            senderSocket.close();
        }
    }

    @Test
    void testFireEventPacketEventTypeCorrect() throws Exception {
        try (DatagramSocket receiverSocket = new DatagramSocket(FireIncidentSubsystem.PORT_SCHEDULER_FIRE)) {
            receiverSocket.setSoTimeout(3000);
            DatagramSocket senderSocket = new DatagramSocket();

            String payload = "14:10:00,2,DRONE_REQUEST,Moderate";
            byte[] data = new byte[payload.getBytes().length + 1];
            data[0] = 0x01;
            System.arraycopy(payload.getBytes(), 0, data, 1, payload.getBytes().length);
            senderSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), FireIncidentSubsystem.PORT_SCHEDULER_FIRE));

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            receiverSocket.receive(packet);

            String[] fields = new String(buffer, 1, packet.getLength() - 1).split(",");
            assertEquals("DRONE_REQUEST", fields[2]);
            senderSocket.close();
        }
    }

    @Test
    void testFireEventPacketSeverityCorrect() throws Exception {
        try (DatagramSocket receiverSocket = new DatagramSocket(FireIncidentSubsystem.PORT_SCHEDULER_FIRE)) {
            receiverSocket.setSoTimeout(3000);
            DatagramSocket senderSocket = new DatagramSocket();

            String payload = "14:03:15,1,FIRE_DETECTED,High";
            byte[] data = new byte[payload.getBytes().length + 1];
            data[0] = 0x01;
            System.arraycopy(payload.getBytes(), 0, data, 1, payload.getBytes().length);
            senderSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), FireIncidentSubsystem.PORT_SCHEDULER_FIRE));

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            receiverSocket.receive(packet);

            String[] fields = new String(buffer, 1, packet.getLength() - 1).split(",");
            assertEquals("High", fields[3]);
            senderSocket.close();
        }
    }

    //UDP packet received by FireIncidentSubsystem to update GUI

    @Test
    void testGuiUpdatePacketFirstByteIsCorrect() throws Exception {
        try (DatagramSocket receiverSocket = new DatagramSocket(FireIncidentSubsystem.PORT_FIRE_SERVER)) {
            receiverSocket.setSoTimeout(3000);
            DatagramSocket senderSocket = new DatagramSocket();

            // format: droneId,status,posX,posY,water,zoneId,severity
            String payload = "1,ARRIVED,350.0,300.0,15.0,1,High";
            byte[] data = new byte[payload.getBytes().length + 1];
            data[0] = 0x04; // TYPE_GUI_UPDATE
            System.arraycopy(payload.getBytes(), 0, data, 1, payload.getBytes().length);
            senderSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), FireIncidentSubsystem.PORT_FIRE_SERVER));

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            receiverSocket.receive(packet);

            assertEquals(0x04, buffer[0]);
            senderSocket.close();
        }
    }

    @Test
    void testGuiUpdatePacketContainsSevenFields() throws Exception {
        try (DatagramSocket receiverSocket = new DatagramSocket(FireIncidentSubsystem.PORT_FIRE_SERVER)) {
            receiverSocket.setSoTimeout(3000);
            DatagramSocket senderSocket = new DatagramSocket();

            String payload = "1,ARRIVED,350.0,300.0,15.0,1,High";
            byte[] data = new byte[payload.getBytes().length + 1];
            data[0] = 0x04;
            System.arraycopy(payload.getBytes(), 0, data, 1, payload.getBytes().length);
            senderSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), FireIncidentSubsystem.PORT_FIRE_SERVER));

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            receiverSocket.receive(packet);

            // droneId, status, posX, posY, water, zoneId, severity
            String[] fields = new String(buffer, 1, packet.getLength() - 1).split(",");
            assertEquals(7, fields.length);
            senderSocket.close();
        }
    }

    @Test
    void testGuiUpdatePacketNoneSeverityWhenReturning() throws Exception {
        try (DatagramSocket receiverSocket = new DatagramSocket(FireIncidentSubsystem.PORT_FIRE_SERVER)) {
            receiverSocket.setSoTimeout(3000);
            DatagramSocket senderSocket = new DatagramSocket();

            // when drone is returning, severity should be NONE
            String payload = "1,RETURNING,175.0,150.0,5.0,0,NONE";
            byte[] data = new byte[payload.getBytes().length + 1];
            data[0] = 0x04;
            System.arraycopy(payload.getBytes(), 0, data, 1, payload.getBytes().length);
            senderSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), FireIncidentSubsystem.PORT_FIRE_SERVER));

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            receiverSocket.receive(packet);

            String[] fields = new String(buffer, 1, packet.getLength() - 1).split(",");
            assertEquals("NONE", fields[6]);
            senderSocket.close();
        }
    }

}
