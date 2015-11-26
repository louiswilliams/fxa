/**
 * RxpServerSocket
 *
 * This class represents an Rxp server socket. It depends on a DatagramSocket for transport and a port to listen on.
 * The socket accepts connections as they come in and returns sockets so a application can communicate with clients.
 *
 */

import java.io.IOException;
import java.net.*;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RxpServerSocket implements RxpReceiver {

    /* Map of addr:port to connected sockets */
    final ConcurrentHashMap<String, RxpSocket> connectedClients;

    /* Queue of clients in the process of connecting */
    final ConcurrentLinkedQueue<RxpSocket> newClients;

    DatagramSocket netEmuSocket;
    short port;
    boolean run;
    private short windowSize;

    private final Object newClientLock;

    /**
     * Create a server socket and start listening for incoming connections
     *
     * @param netEmuSocket The DatagramSocket used for transport
     * @param listenPort This is the RXP port to bind and listen on
     */
    public RxpServerSocket(DatagramSocket netEmuSocket, short listenPort) {
        this.port = listenPort;
        this.netEmuSocket = netEmuSocket;
        windowSize = 1;

        connectedClients = new ConcurrentHashMap<>();
        newClients = new ConcurrentLinkedQueue<>();

        newClientLock = new Object();
        /* Start listening for data */
        receiverStart();
    }


    /**
     * This function blocks until a client attempts to connect and has done so successfully. A RxpSocket is returned
     * which is used to communicate with a client
     *
     * @return Connected client socket
     * @throws IOException This is thrown if there are any errors which prevent the connection from being established,
     * invalid authorization or a timeout during the connection stage.
     */
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


    /**
     * This RxpReceiver reads DatagramPackets into RxpPackets and sends them to the appropriate socket.
     *
     * If a packet is corrupt or it's destination does not match the server's listening port, the packet is dropped
     *
     * If a packet is not associated with a socket, a new RxpSocket is created and an attempt to establish a connection
     * is made. If a call to accept() has been made, it will return when the connection has been established.
     *
     * If a packet is associated with a socket that is currently being established, the packet is sent to the socket.
     *
     * If a packet is associate with a socket that is already established, the packet is sent to the socket.
     */
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
                        socket.setRecvWindowSize(windowSize);
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

    /* Stop receiving packets, shutdown the server */
    @Override
    public void receiverStop() {
        run = false;
    }

    private RxpSocket findNewClientByKey(String key) {
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
        return getKey(socket.getDestination(), socket.getDestPort());
    }

    private static String getKey(InetAddress addr, int destPort) {
        return addr.getHostAddress() + ":" + destPort;
    }

    public void close() {
        receiverStop();
    }

    public void setWindowSize(short size){
        windowSize = size;
    }

    public short getWindowSize(){
        return windowSize;
    }
}
