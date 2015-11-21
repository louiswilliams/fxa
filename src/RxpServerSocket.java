import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RxpServerSocket implements RxpReceiver {

    ConcurrentHashMap<String, RxpSocket> connectedClients;
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

        receiverStart();
    }


    public RxpSocket accept() {
        RxpSocket client = null;
        try {
            synchronized (newClients) {
                while (newClients.isEmpty()) {
                    newClients.wait();
                }
                System.out.println("ACCEPTED CONNECTION");
                client = newClients.poll();

                InputStream inputStream = client.getInputStream();
                OutputStream outputStream = client.getOutputStream();

                // TODO: Do synchronization here
                connectedClients.put(getKey(client), client);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
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

    private static String getKey(RxpSocket socket) {
        return getKey(socket.destination, socket.destPort);
    }

    private static String getKey(InetAddress addr, int destPort) {
        return addr.toString() + ":" + destPort;
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
                        socket.receivePacket(packet);
                    } else if ((socket = findNewClientByKey(receivedKey)) != null) {
                        socket.receivePacket(packet);
                    } else {
                        socket = new RxpSocket(netEmuSocket, datagramPacket.getAddress(), packet.srcPort, RxpServerSocket.this);
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

    public void close() {
        receiverStop();
    }

    @Override
    public void receiverStop() {
        run = false;
    }
}
