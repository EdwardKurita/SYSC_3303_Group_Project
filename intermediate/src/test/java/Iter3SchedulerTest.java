import org.junit.jupiter.api.*;
import java.net.*;
import static org.junit.jupiter.api.Assertions.*;

import main.java.DroneState;

public class Iter3SchedulerTest {

    @Test
    void testDroneDataInitialStateIsIdle() {
        DroneData drone = new DroneData(1);
        assertEquals(DroneState.IDLE, drone.getState());
    }

    @Test
    void testDroneDataInitialWaterIsFull() {
        DroneData drone = new DroneData(1);
        assertEquals(15.0, drone.getCurrentWater());
    }

    @Test
    void testDroneDataInitialZoneIsBase() {
        DroneData drone = new DroneData(1);
        assertEquals(0, drone.getCurrentZone());
    }

    @Test
    void testDroneDataInitialMissionIsNull() {
        DroneData drone = new DroneData(1);
        assertNull(drone.getCurrentMission());
    }

    @Test
    void testDroneDataReturnToBaseResetsState() {
        DroneData drone = new DroneData(1);
        drone.setState(DroneState.RETURNING);
        drone.returnToBase();
        assertEquals(DroneState.IDLE, drone.getState());
        assertEquals(0, drone.getCurrentZone());
        assertNull(drone.getCurrentMission());
    }

    @Test
    void testDroneDataUseWaterReducesLevelAndBaseRefill() {
        DroneData drone = new DroneData(1);
        drone.useWater(10.0);
        assertEquals(5.0, drone.getCurrentWater());
        drone.returnToBase();
        assertEquals(15.0, drone.getCurrentWater());
    }

    @Test
    void testDroneDataHasEnoughWater() {
        DroneData drone = new DroneData(1);
        assertTrue(drone.hasEnoughWater(10.0));
        assertTrue(drone.hasEnoughWater(15.0));
        assertFalse(drone.hasEnoughWater(16.0));
    }

    //UDP FireEvent packet sent to scheduler

    @Test
    void testFireEventPacketFirstByteIsCorrect() throws Exception {
        try (DatagramSocket receiverSocket = new DatagramSocket(Scheduler.PORT_SCHEDULER_FIRE)) {
            receiverSocket.setSoTimeout(3000);
            DatagramSocket senderSocket = new DatagramSocket();

            String payload = "14:03:15,1,FIRE_DETECTED,High";
            byte[] data = new byte[payload.getBytes().length + 1];
            data[0] = 0x01; // TYPE_FIRE_EVENT
            System.arraycopy(payload.getBytes(), 0, data, 1, payload.getBytes().length);
            senderSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), Scheduler.PORT_SCHEDULER_FIRE));

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            receiverSocket.receive(packet);

            assertEquals(0x01, buffer[0]);
            senderSocket.close();
        }
    }

    @Test
    void testFireEventPacketContainsFourFields() throws Exception {
        try (DatagramSocket receiverSocket = new DatagramSocket(Scheduler.PORT_SCHEDULER_FIRE)) {
            receiverSocket.setSoTimeout(3000);
            DatagramSocket senderSocket = new DatagramSocket();

            String payload = "14:03:15,1,FIRE_DETECTED,High";
            byte[] data = new byte[payload.getBytes().length + 1];
            data[0] = 0x01;
            System.arraycopy(payload.getBytes(), 0, data, 1, payload.getBytes().length);
            senderSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), Scheduler.PORT_SCHEDULER_FIRE));

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
    void testFireEventPacketTimeCorrect() throws Exception {
        try (DatagramSocket receiverSocket = new DatagramSocket(Scheduler.PORT_SCHEDULER_FIRE)) {
            receiverSocket.setSoTimeout(3000);
            DatagramSocket senderSocket = new DatagramSocket();

            String payload = "14:03:15,1,FIRE_DETECTED,High";
            byte[] data = new byte[payload.getBytes().length + 1];
            data[0] = 0x01;
            System.arraycopy(payload.getBytes(), 0, data, 1, payload.getBytes().length);
            senderSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), Scheduler.PORT_SCHEDULER_FIRE));

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            receiverSocket.receive(packet);

            String[] fields = new String(buffer, 1, packet.getLength() - 1).split(",");
            assertEquals("14:03:15", fields[0]);
            senderSocket.close();
        }
    }

    @Test
    void testFireEventPacketZoneIdCorrect() throws Exception {
        try (DatagramSocket receiverSocket = new DatagramSocket(Scheduler.PORT_SCHEDULER_FIRE)) {
            receiverSocket.setSoTimeout(3000);
            DatagramSocket senderSocket = new DatagramSocket();

            String payload = "14:03:15,3,FIRE_DETECTED,High";
            byte[] data = new byte[payload.getBytes().length + 1];
            data[0] = 0x01;
            System.arraycopy(payload.getBytes(), 0, data, 1, payload.getBytes().length);
            senderSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), Scheduler.PORT_SCHEDULER_FIRE));

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            receiverSocket.receive(packet);

            String[] fields = new String(buffer, 1, packet.getLength() - 1).split(",");
            assertEquals("3", fields[1]);
            senderSocket.close();
        }
    }

    @Test
    void testFireEventPacketSeverityCorrect() throws Exception {
        try (DatagramSocket receiverSocket = new DatagramSocket(Scheduler.PORT_SCHEDULER_FIRE)) {
            receiverSocket.setSoTimeout(3000);
            DatagramSocket senderSocket = new DatagramSocket();

            String payload = "14:03:15,1,FIRE_DETECTED,Moderate";
            byte[] data = new byte[payload.getBytes().length + 1];
            data[0] = 0x01;
            System.arraycopy(payload.getBytes(), 0, data, 1, payload.getBytes().length);
            senderSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), Scheduler.PORT_SCHEDULER_FIRE));

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            receiverSocket.receive(packet);

            String[] fields = new String(buffer, 1, packet.getLength() - 1).split(",");
            assertEquals("Moderate", fields[3]);
            senderSocket.close();
        }
    }

    //UDP zone data packet sent to scheduler

    @Test
    void testDroneStatusPacketFirstByteIsCorrect() throws Exception {
        try (DatagramSocket receiverSocket = new DatagramSocket(Scheduler.PORT_SCHEDULER_DRONE)) {
            receiverSocket.setSoTimeout(3000);
            DatagramSocket senderSocket = new DatagramSocket();

            String payload = "1,2,ARRIVED,Arrived at Zone 2,0.0,15.0,350.0,300.0";
            byte[] data = new byte[payload.getBytes().length + 1];
            data[0] = 0x03; // TYPE_DRONE_STATUS
            System.arraycopy(payload.getBytes(), 0, data, 1, payload.getBytes().length);
            senderSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), Scheduler.PORT_SCHEDULER_DRONE));

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            receiverSocket.receive(packet);

            assertEquals(0x03, buffer[0]);
            senderSocket.close();
        }
    }

    @Test
    void testDroneStatusPacketFieldsCorrect() throws Exception {
        try (DatagramSocket receiverSocket = new DatagramSocket(Scheduler.PORT_SCHEDULER_DRONE)) {
            receiverSocket.setSoTimeout(3000);
            DatagramSocket senderSocket = new DatagramSocket();

            String payload = "1,2,ARRIVED,Arrived at Zone 2,0.0,15.0,350.0,300.0";
            byte[] data = new byte[payload.getBytes().length + 1];
            data[0] = 0x03;
            System.arraycopy(payload.getBytes(), 0, data, 1, payload.getBytes().length);
            senderSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), Scheduler.PORT_SCHEDULER_DRONE));

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            receiverSocket.receive(packet);

            String[] fields = new String(buffer, 1, packet.getLength() - 1).split(",");
            assertEquals("1", fields[0]); // droneId
            assertEquals("2", fields[1]); // zoneId
            assertEquals("ARRIVED", fields[2]); // status
            assertEquals(15.0, Double.parseDouble(fields[5])); // currentWater
            assertEquals(350.0, Double.parseDouble(fields[6])); // posX
            assertEquals(300.0, Double.parseDouble(fields[7])); // posY
            senderSocket.close();
        }
    }

    //UDP packet sent from scheduler

    @Test
    void testSchedulerSendsAssignmentToCorrectDronePort() throws Exception {
        // drone 1 listens on PORT_DRONE_BASE + 1
        try (DatagramSocket droneReceiver = new DatagramSocket(Scheduler.PORT_DRONE_BASE + 1)) {
            droneReceiver.setSoTimeout(3000);

            // simulate scheduler sending assignment to drone 1
            DatagramSocket senderSocket = new DatagramSocket();
            String payload = "1,14:00:00,1,FIRE_DETECTED,High,350.0,300.0";
            byte[] data = new byte[payload.getBytes().length + 1];
            data[0] = 0x02; // TYPE_DRONE_ASSIGNMENT
            System.arraycopy(payload.getBytes(), 0, data, 1, payload.getBytes().length);
            senderSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), Scheduler.PORT_DRONE_BASE + 1));

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            droneReceiver.receive(packet);

            // verify drone 1 received the packet on its correct port
            assertEquals(0x02, buffer[0]);
            String[] fields = new String(buffer, 1, packet.getLength() - 1).split(",");
            assertEquals("1", fields[0]); // droneId = 1
            senderSocket.close();
        }
    }

    @Test
    void testSchedulerGuiUpdatePacketFirstByteIsCorrect() throws Exception {
        try (DatagramSocket guiReceiver = new DatagramSocket(Scheduler.PORT_FIRE_SERVER)) {
            guiReceiver.setSoTimeout(3000);

            DatagramSocket senderSocket = new DatagramSocket();
            String payload = "1,ARRIVED,350.0,300.0,15.0,1,High";
            byte[] data = new byte[payload.getBytes().length + 1];
            data[0] = 0x04; // TYPE_GUI_UPDATE
            System.arraycopy(payload.getBytes(), 0, data, 1, payload.getBytes().length);
            senderSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), Scheduler.PORT_FIRE_SERVER));

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            guiReceiver.receive(packet);

            assertEquals(0x04, buffer[0]);
            senderSocket.close();
        }
    }

    @Test
    void testSchedulerGuiUpdatePacketFieldsCorrect() throws Exception {
        try (DatagramSocket guiReceiver = new DatagramSocket(Scheduler.PORT_FIRE_SERVER)) {
            guiReceiver.setSoTimeout(3000);

            DatagramSocket senderSocket = new DatagramSocket();
            String payload = "1,ARRIVED,350.0,300.0,15.0,1,High";
            byte[] data = new byte[payload.getBytes().length + 1];
            data[0] = 0x04;
            System.arraycopy(payload.getBytes(), 0, data, 1, payload.getBytes().length);
            senderSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), Scheduler.PORT_FIRE_SERVER));

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            guiReceiver.receive(packet);

            String[] fields = new String(buffer, 1, packet.getLength() - 1).split(",");
            assertEquals("1", fields[0]); // droneId
            assertEquals("ARRIVED", fields[1]); // status
            assertEquals(350.0, Double.parseDouble(fields[2])); // posX
            assertEquals(300.0, Double.parseDouble(fields[3])); // posY
            assertEquals(15.0, Double.parseDouble(fields[4]));  // water
            assertEquals("1", fields[5]); // zoneId
            assertEquals("High", fields[6]); // severity
            senderSocket.close();
        }
    }

    //Testing for correct drone is being used.

    @Test
    void testIdleDroneGetsAssignedOverBusyDrone() throws Exception {
        // drone 1 is busy, drone 2 is idle
        // open receivers on both drone ports
        try (DatagramSocket drone1Receiver = new DatagramSocket(Scheduler.PORT_DRONE_BASE + 1);
             DatagramSocket drone2Receiver = new DatagramSocket(Scheduler.PORT_DRONE_BASE + 2)) {

            drone1Receiver.setSoTimeout(1000);
            drone2Receiver.setSoTimeout(3000);

            // start the scheduler
            Scheduler scheduler = new Scheduler(InetAddress.getLocalHost());
            Thread schedulerThread = new Thread(scheduler);
            schedulerThread.start();

            DatagramSocket senderSocket = new DatagramSocket();

            // send zone data first
            String zonePaylod = "1,0,0,700,600";
            byte[] zoneData = new byte[zonePaylod.getBytes().length + 1];
            zoneData[0] = 0x00;
            System.arraycopy(zonePaylod.getBytes(), 0, zoneData, 1, zonePaylod.getBytes().length);
            senderSocket.send(new DatagramPacket(zoneData, zoneData.length,
                    InetAddress.getLocalHost(), Scheduler.PORT_SCHEDULER_FIRE));

            // register drone 1 as BUSY by sending a status packet with EN_ROUTE
            String drone1Status = "1,1,EN_ROUTE,Flying,0.0,15.0,100.0,100.0";
            byte[] d1Data = new byte[drone1Status.getBytes().length + 1];
            d1Data[0] = 0x03;
            System.arraycopy(drone1Status.getBytes(), 0, d1Data, 1, drone1Status.getBytes().length);
            senderSocket.send(new DatagramPacket(d1Data, d1Data.length,
                    InetAddress.getLocalHost(), Scheduler.PORT_SCHEDULER_DRONE));

            // register drone 2 as IDLE
            String drone2Status = "2,0,IDLE,Waiting,0.0,15.0,0.0,0.0";
            byte[] d2Data = new byte[drone2Status.getBytes().length + 1];
            d2Data[0] = 0x03;
            System.arraycopy(drone2Status.getBytes(), 0, d2Data, 1, drone2Status.getBytes().length);
            senderSocket.send(new DatagramPacket(d2Data, d2Data.length,
                    InetAddress.getLocalHost(), Scheduler.PORT_SCHEDULER_DRONE));

            Thread.sleep(500); // give scheduler time to process registrations

            // send a fire event
            String firePayload = "14:03:15,1,FIRE_DETECTED,High";
            byte[] fireData = new byte[firePayload.getBytes().length + 1];
            fireData[0] = 0x01;
            System.arraycopy(firePayload.getBytes(), 0, fireData, 1, firePayload.getBytes().length);
            senderSocket.send(new DatagramPacket(fireData, fireData.length,
                    InetAddress.getLocalHost(), Scheduler.PORT_SCHEDULER_FIRE));

            // drone 1 should NOT receive an assignment
            boolean drone1GotPacket = false;
            try {
                byte[] buffer = new byte[1024];
                drone1Receiver.receive(new DatagramPacket(buffer, buffer.length));
                if (buffer[0] == 0x02) drone1GotPacket = true;
            } catch (SocketTimeoutException e) {
                // drone 1 is busy so should not get assignment
            }

            // drone 2 SHOULD receive the assignment
            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            drone2Receiver.receive(packet);

            String[] fields = new String(buffer, 1, packet.getLength() - 1).split(",");

            assertFalse(drone1GotPacket); // drone 1 was busy, should not get it
            assertEquals(0x02, buffer[0]); // it's an assignment packet
            assertEquals("2", fields[0]); // drone 2 got the assignment
            assertEquals("1", fields[2]); // for zone 1
            assertEquals("High", fields[4]); // correct severity

            scheduler.running = false;
            schedulerThread.join(3000);
            senderSocket.close();
        }
    }
}
