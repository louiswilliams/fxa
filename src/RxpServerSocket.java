import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RxpServerSocket implements RxpReceiver {

    final ConcurrentHashMap<String, RxpSocket> connectedClients;
    final ConcurrentLinkedQueue<RxpSocket> newClients;
    DatagramSocket netEmuSocket;
    short port;
    boolean run;


    public static final int MTU = 1500;

    public RxpServerSocket(DatagramSocket netEmuSocket, short listenPort) throws SocketException, UnknownHostException {
        this.port = listenPort;
        this.netEmuSocket = netEmuSocket;

        connectedClients = new ConcurrentHashMap<>();
        newClients = new ConcurrentLinkedQueue<>();

        /* Start listening for data */
        receiverStart();
    }


    public RxpSocket accept() {
        RxpSocket client = null;
        try {
            synchronized (newClients) {
                while (newClients.isEmpty()) {
                    newClients.wait();
                }
                client = newClients.poll();
                System.out.println("Accepted: " + client);

                client.waitForConnection();

                connectedClients.put(getKey(client), client);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
        return client;
    }

    public RxpSocket findNewClientByKey(String key) {
        RxpSocket socket = null;

        RxpSocket current;
        Iterator<RxpSocket> it = newClients.iterator();
        while (socket == null && it.hasNext()) {
            current = it.next();
            if (getKey(current).equals(key)) {
                socket = current;
            }
        }
        return socket;
    }

    @Override
    public void receiverStart() {
        /* Data reception loop */
        new Thread(() -> {
            run = true;
            while (run) {
                byte[] buffer = new byte[MTU];
                DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
                try {
                    netEmuSocket.receive(datagramPacket);
                    RxpPacket packet = new RxpPacket(datagramPacket.getData(), datagramPacket.getLength());
                    String receivedKey = getKey(datagramPacket.getAddress(), packet.srcPort);
                    System.out.println("Received from " + receivedKey);

                    if (packet.destPort != port) {
                        throw new IOException("Received on wrong port!");
                    }

                    RxpSocket socket;
                    if ((socket = connectedClients.get(receivedKey)) != null) {
                        System.out.println("Packet received on an existing socket: " + receivedKey);
                        socket.receivePacket(packet);
                    } else if ((socket = findNewClientByKey(receivedKey)) != null) {
                        System.out.println("Packet received on a connecting socket: " + receivedKey);
                        socket.receivePacket(packet);
                    } else {
                        System.out.println("Packet received, but no socket exists: " + receivedKey);
                        socket = new RxpSocket(netEmuSocket, port, RxpServerSocket.this);
                        socket.attach(datagramPacket.getAddress(), packet.srcPort);
                        socket.receivePacket(packet);
                        synchronized (newClients) {
                            newClients.add(socket);
                            newClients.notify();
                        }
                    }
                } catch (IOException e) {
                    System.err.println(e.getMessage());
                } catch (InvalidChecksumException e) {
                    System.err.println("Dropping packet due to incorrect checksum");
                }
            }
        }).start();

    }

    private static String getKey(RxpSocket socket) {
        return getKey(socket.getDestination(), socket.getDestPort());
    }

    private static String getKey(InetAddress addr, int destPort) {
        String key = addr.getHostAddress() + ":" + destPort;
        return key;
    }

    public void close() {
        receiverStop();
    }

    @Override
    public void receiverStop() {
        run = false;
    }
}
