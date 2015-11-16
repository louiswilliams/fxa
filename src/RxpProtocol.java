import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class RxpProtocol {

    short port;
    DatagramSocket socket;

    public static final int MAX_MTU = 1500;

    public RxpProtocol(short port) throws SocketException {
        this.port = port;
        socket = new DatagramSocket(port);
    }

    private RxpPacket receivePacket() throws IOException {
        byte[] buffer = new byte[MAX_MTU];
        DatagramPacket udpPacket = new DatagramPacket(buffer, buffer.length);
        socket.receive(udpPacket);

        RxpPacket packet = null;
        while (packet == null) {
            try {
                packet = new RxpPacket(udpPacket.getData(), udpPacket.getLength());
            } catch (InvalidChecksumException e) {
                System.err.println("Dropping packet due to invalid checksum");
            }
        }

        return packet;
    }

    private void sendPacket(RxpPacket packet) throws IOException {
        byte[] buffer = packet.getBytes();

//        DatagramPacket udpPacket = new DatagramPacket(buffer, buffer.length, address, destPort);
//        socket.send(udpPacket);
    }

}
