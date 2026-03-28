import org.junit.jupiter.api.*;
import java.net.*;
import static org.junit.jupiter.api.Assertions.*;

public class Iter4Test {

    @Test
    void guiReceivesFaultType() throws Exception {
        try (DatagramSocket receiver = new DatagramSocket(FireIncidentSubsystem.PORT_FIRE_SERVER)) {
            receiver.setSoTimeout(3000);

            DatagramSocket sender = new DatagramSocket();

            String payload = "1,FAULTED,0,0,15,1,High,NOZZLE_JAMMED";
            byte[] data = new byte[payload.length() + 1];
            data[0] = 0x04;

            System.arraycopy(payload.getBytes(), 0, data, 1, payload.length());

            sender.send(new DatagramPacket(
                    data,
                    data.length,
                    InetAddress.getLocalHost(),
                    FireIncidentSubsystem.PORT_FIRE_SERVER
            ));

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            receiver.receive(packet);

            String msg = new String(buffer, 1, packet.getLength() - 1);

            assertTrue(msg.contains("NOZZLE_JAMMED"));

            sender.close();
        }
    }
}