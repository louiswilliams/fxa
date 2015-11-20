import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class RxpProtocol {

    DatagramSocket socket;
    ConcurrentHashMap<String, DataListener> dataListeners; //key = address + srcPort + destPort

    public static final int MAX_MTU = 1500;

    public RxpProtocol(InetAddress addr, short port) throws SocketException {
        socket = new DatagramSocket();
        socket.connect(addr, port);
        dataListeners = new ConcurrentHashMap<>();

        /* Data reception loop */
        new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] buffer = new byte[MAX_MTU];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                    String receivedKey = getKey(packet.getPort(), socket.getPort());
                    System.out.println("Received " + receivedKey);

                    DataListener listener = dataListeners.get(receivedKey);
                    if (listener != null) {
                        listener.received(packet.getData(), packet.getLength());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void addInputStreamListener(RxpSocket socket, DataListener listener) {
        dataListeners.put(getKey(socket), listener);
    }

    public void removeInputStreamListener(RxpSocket socket) {
        dataListeners.remove(getKey(socket));
    }

    public void registerSocket(RxpSocket socket) {
        if (dataListeners.containsValue(socket)) {
            // TODO: Throw exception
        } else {
            String key = getKey(socket);
        }
    }

    public void start() {

        new Thread(() -> {
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
//                    RxpSocket connection = dataListeners.get(udpPacket.getAddress().toString()
//                            + packet.srcPort + packet.destPort);
//                    if (connection != null) {
//                        connection.receivePacket(packet);
//                    }
                } catch (InvalidChecksumException e) {
                    e.printStackTrace();
                }
            }
        }).start();

    }

    public byte[] receiveFrom(InetAddress remote, short port) {
//        RxpSocket connection = dataListeners.get(remote.toString() + port);
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

    private static String getKey(RxpSocket s) {
        return s.srcPort + ":" + s.destPort;
    }

    private static String getKey(int srcPort, int destPort) {
        return srcPort + ":" + destPort;
    }
}
