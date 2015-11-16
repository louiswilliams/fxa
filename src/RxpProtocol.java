import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.HashMap;
import java.util.HashSet;

public class RxpProtocol {

    short port;
    DatagramSocket socket;
    HashSet<RxpSocket> connections;

    public static final int MAX_MTU = 1500;

    public RxpProtocol(short port) throws SocketException {
        this.port = port;
        socket = new DatagramSocket(port);
        connections = new HashSet<RxpSocket>();
    }

    public void registerSocket(RxpSocket socket) {
        if (connections.contains(socket)) {
            // TODO: THrow exception
        } else {
            connections.add(socket);
        }
    }

    public void start() {

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    byte[] buffer = new byte[MAX_MTU];
                    DatagramPacket udpPacket = new DatagramPacket(buffer, buffer.length);
                    try {
                        socket.receive(udpPacket);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        RxpPacket packet = new RxpPacket(buffer, udpPacket.getLength());

                        // TODO: Redo connections to a hashmap of addr + destPort + srcPort -> RxpSocket
//                        RxpSocket connection = connections.get(udpPacket.getAddress().toString() + packet.destPort);
//                        if (connection != null) {
//                            connection.wri (packet);
//                        }
                    } catch (InvalidChecksumException e) {
                        e.printStackTrace();
                    }

                }
            }
        }).start();

    }

    public byte[] receiveFrom(InetAddress remote, short port) {
//        RxpSocket connection = connections.get(remote.toString() + port);
//
//        if (connection != null) {
//            return connection.data;
//        }
        return null;
    }

    private RxpPacket receivePacket(short source) throws IOException {


//        RxpPacket packet = null;
//        while (packet == null) {
//            try {
//                packet = new RxpPacket(udpPacket.getData(), udpPacket.getLength());
//            } catch (InvalidChecksumException e) {
//                System.err.println("Dropping packet due to invalid checksum");
//            }
//        }

        return null;
    }

    private void sendPacket(RxpPacket packet) throws IOException {
        byte[] buffer = packet.getBytes();

//        DatagramPacket udpPacket = new DatagramPacket(buffer, buffer.length, address, destPort);
//        socket.send(udpPacket);
    }

}
