import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.DatagramSocket;
import java.util.Random;

public class RxpPacketTest {

    RxpSocket socket;
    Random random;

    @BeforeMethod
    public void setUp() throws Exception {
        DatagramSocket datagramSocket = new DatagramSocket();
        socket = new RxpSocket(datagramSocket);
        random = new Random(socket.hashCode());
    }

    @AfterMethod
    public void tearDown() throws Exception {

    }

    @Test
    public void testGetBytes() throws Exception {
        for (int i = 0; i < 100; i++) {
            RxpPacket packet = new RxpPacket(socket);
            String message = "Hello message!";
            packet.data = message.getBytes();

            byte[] bytes = packet.getBytes();
            corrupt(bytes);

            InvalidChecksumException exception = null;
            try {
                System.out.println("Test " + i);
                RxpPacket check = new RxpPacket(bytes, bytes.length);
                Assert.assertFalse(packet.equals(check));
            } catch (InvalidChecksumException e) {
                exception = e;
            }
            Assert.assertNotNull(exception);
        }
    }

    public void corrupt(byte[] bytes) {
        for (int i = 0; i < 8; i ++) {
            int r1 = random.nextInt(bytes.length);
            int r2 = random.nextInt(bytes.length);
            byte tmp = bytes[r1];
            bytes[r1] = bytes[r2];
            bytes[r2] = tmp;
        }
    }
}