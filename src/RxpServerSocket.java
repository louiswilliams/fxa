import java.io.IOException;
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
    private short windowSize;

    private final Object newClientLock;

    public RxpServerSocket(DatagramSocket netEmuSocket, short listenPort) throws SocketException, UnknownHostException {
        this.port = listenPort;
        this.netEmuSocket = netEmuSocket;
        windowSize = 1;

        connectedClients = new ConcurrentHashMap<>();
        newClients = new ConcurrentLinkedQueue<>();

        newClientLock = new Object();
        /* Start listening for data */
        receiverStart();
    }


    public RxpSocket accept() throws IOException{
        RxpSocket client = null;
        try {
            synchronized (newClientLock) {
                while (newClients.isEmpty()) {
                    newClientLock.wait();
                }
                client = newClients.peek();
                client.waitForConnection();

                newClients.remove(client);
                connectedClients.put(getKey(client), client);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            if (client != null) {
                newClients.remove(client);
            }
            throw e;
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
            System.out.println("Listening on (ip_addr:udp_port:rxp_port)" + netEmuSocket.getLocalAddress().getHostAddress() + ":" + netEmuSocket.getLocalPort() + ":" + port);
            while (run) {
                byte[] buffer = new byte[RxpSocket.UDP_MAX];
                DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
                RxpSocket socket = null;
                try {
                    netEmuSocket.receive(datagramPacket);
                    RxpPacket packet = new RxpPacket(datagramPacket.getData(), datagramPacket.getLength());
                    String receivedKey = getKey(datagramPacket.getAddress(), packet.srcPort);

                    if (packet.destPort != port) {
                        throw new IOException("Received on wrong port: " + packet.destPort);
                    }

                    if ((socket = connectedClients.get(receivedKey)) != null) {
                        socket.receivePacket(packet);
                    } else if ((socket = findNewClientByKey(receivedKey)) != null) {
                        System.out.println("Packet received on a connecting socket: " + receivedKey);
                        socket.receivePacket(packet);
                    } else {
                        System.out.println("Creating client socket for " + receivedKey);
                        socket = new RxpSocket(netEmuSocket, port, RxpServerSocket.this);
                        socket.attach(datagramPacket.getAddress(), packet.srcPort);
                        socket.receivePacket(packet);
                        newClients.add(socket);
                        synchronized (newClientLock) {
                            newClientLock.notify();
                        }
                    }
                } catch (IOException e) {
                    if (socket != null) {
                        if (socket.getState() != RxpState.CLOSED) {
                            socket.close();
                        }
                        if (connectedClients.containsKey(getKey(socket))) {
                            connectedClients.remove(getKey(socket));
                        } else {
                            newClients.remove();
                        }
                    }
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
        return addr.getHostAddress() + ":" + destPort;
    }

    public void close() {
        receiverStop();
    }

    @Override
    public void receiverStop() {
        run = false;
    }

    public void setWindowSize(short size){
        windowSize = size;
    }

    public short getWindowSize(){
        return windowSize;
    }
}
