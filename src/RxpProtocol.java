import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.HashMap;

public class RxpProtocol {

    short port;
    DatagramSocket socket;
    HashMap<String, RxpSocket> connections; //key = address + srcPort + destPort

    public static final int MAX_MTU = 1500;

    public RxpProtocol(short port) throws SocketException {
        this.port = port;
        socket = new DatagramSocket(port);
        connections = new HashMap<>();
    }

    public void registerSocket(RxpSocket socket) {
        if (connections.containsValue(socket)) {
            // TODO: Throw exception
        } else {
            String key = socket.address.toString() + socket.srcPort + socket.destPort;
            connections.put(key, socket);
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
                        RxpSocket connection = connections.get(udpPacket.getAddress().toString()
                                + packet.srcPort + packet.destPort);
                        if (connection != null) {
                            connection.receivePacket(packet);
                        }
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
