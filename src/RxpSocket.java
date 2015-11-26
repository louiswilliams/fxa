import java.io.*;
import java.net.*;
import java.util.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RxpSocket implements RxpReceiver {

    DatagramSocket netEmuSocket;

    private short srcPort;
    private short destPort;

    private int lastAck;
    private int sequenceNum;

    private InetAddress destination;
    private short sendWindowSize;
    private short recvWindowSize;

    public static final int MSS = 1500;
    public static final int UDP_MAX = 65536;
    public static final int RECV_TIMEOUT = 1500;
    public static final int SOCK_TIMEOUT = 60000;


    private RxpState state;

    private Random rand;
    private String hash = "";

    private RxpInputStream inputStream;
    private RxpOutputStream outputStream;

    long lastSendTime;
    long lastRecvTime;

    RxpReceiver dataReceiver;

    boolean receiverRun = true;
    boolean debugEnabled = true;
    final Object connectLock;
    final Object timeoutLock;

    final Queue<RxpPacket> sendWindow;

    /**
     * Create a socket with a random source port
     *
     * @param netEmuSocket NetEmu UDP socket
     */
    public RxpSocket(DatagramSocket netEmuSocket) {
        this(netEmuSocket, (short) (new Random().nextInt(Short.MAX_VALUE - 1024) + 1024));
    }

    /**
     * Create a socket given a NetEmu UDP socket and a Rxp port to listen on
     *
     * @param netEmuSocket NetEmu UDP socket for our connection
     * @param listen Rxp port to listen on
     */
    public RxpSocket(DatagramSocket netEmuSocket, short listen) {
        Random r = new Random();
        this.netEmuSocket = netEmuSocket;
        this.srcPort = listen;
        state = RxpState.CLOSED;
        rand = new Random();
        sequenceNum = r.nextInt(Integer.MAX_VALUE);

        sendWindow = new ConcurrentLinkedQueue<>();

        inputStream = new RxpInputStream(10 * MSS);
        outputStream = new RxpOutputStream(this, 10 * MSS);
        dataReceiver = this;
        connectLock = new Object();
        timeoutLock = new Object();
        sendWindowSize = 1;
        recvWindowSize = 1;
    }

    /**
     * Create a socket and override the default RxpReceiver. This is used by RxpServerSockets to
     * handle multiple incoming connections.
     *
     * @param netEmuSocket NetEmu UDP connection
     * @param listen Listen port. This will be the server's destination port
     * @param dataReceiver The server's RxpDataReceiver
     */
    public RxpSocket(DatagramSocket netEmuSocket, short listen, RxpReceiver dataReceiver) {
        this(netEmuSocket, listen);
        this.dataReceiver = dataReceiver;
    }

    /**
     * Attaches a socket to a remote host at a given port, but does not initiate a connection.
     * Used by RxpServerSocket to provide sockets when accept() is called.
     *
     * This also starts the reception timeout thread
     * If no packets have been received in SOCK_TIMEOUT, the connection is closed due to inactivity
     * If no packets have been received in RECV_TIMEOUT and the send window is not empty, the entire window is resent.
     *
     * @param dest Destination address
     * @param destPort Destination port
     */
    void attach(InetAddress dest, short destPort) {
        this.destPort = destPort;
        this.destination = dest;

        state = RxpState.LISTEN;

        /* Start the timer here */
        updateLastReceived();

        new Thread(() -> {
            long recvDiff = 0;
            do {
                synchronized (timeoutLock) {
                    recvDiff = System.currentTimeMillis() - lastRecvTime;
                    if (recvDiff >= SOCK_TIMEOUT) {
                        System.err.println("Socket inactive, timing out.");
                        close();
                        return;
                    } else if (sendWindow.size() > 0 && recvDiff >= RECV_TIMEOUT) {
                        try {
                            resendWindow();
                            recvDiff = 0;
                        } catch (IOException e) {
                            close();
                            return;
                        }
                    } else {
                        recvDiff = 0;
                    }
                }
                try {
                    Thread.sleep(RECV_TIMEOUT - recvDiff);
                } catch (InterruptedException e){
                    e.printStackTrace();
                    break;
                }
            } while (state != RxpState.CLOSED);
        }).start();
    }

    /**
     * Attach a socket to a remote host and initiate a connection. Blocks until connection is established
     * or an IOException is thrown due to a connection establishment problem
     *
     * @param dest Destination host
     * @param destPort Destination port
     * @throws IOException
     */
    public void connect(InetAddress dest, short destPort) throws IOException {
        attach(dest, destPort);

        /* Only start receiving if we are the data receiver */
        if (dataReceiver  == this) {
            dataReceiver.receiverStart();
        }

        printDebug("Connecting");
        sendSyn();
        waitForConnection();
    }

    /**
     * Block and wait for connection to become established. Called by RxpServerSocket when using accept()
     * and RxpSocket when initiating a connection.
     *
     * @throws IOException Connection could not be established and was closed
     */
    void waitForConnection() throws IOException {
        try {
            synchronized (connectLock) {
                while (state != RxpState.ESTABLISHED && state != RxpState.CLOSED) {
                    connectLock.wait();
                }
                if (state == RxpState.ESTABLISHED) {
                    printDebug("Connection established");
                } else {
                    throw new IOException("Connection closed");
                }
            }
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    /**
     * Close a socket by stopping packet reception, and invalidating the input and output streams
     */
    public void close() {
        synchronized (connectLock) {
            state = RxpState.CLOSED;
            if (dataReceiver == this) {
                dataReceiver.receiverStop();
            }
            inputStream.close();
            outputStream.close();
            connectLock.notify();
        }
    }

    void sendPacketWithAck(RxpPacket packet, int ack) throws IOException {
        lastAck = ack;
        sendPacket(packet);
    }

    /**
     * Retransmit an already-sent packet. The acknowledgement is updated with the most recently acknowledged packet
     *
     * @param packet Old packet to retransmit
     * @throws IOException Thrown by the UDP transport layer
     */
    private void resendPacket(RxpPacket packet) throws IOException {
        packet.acknowledgement = lastAck;
        byte[] buffer = packet.getBytes();

        printDebug("Re-sending (" + state + "): " + packet);

        updateLastSent();
        DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
        netEmuSocket.send(datagramPacket);
    }

    /**
     * Resend the the entire window in the case that a connection has reached a RECV_TIMEOUT
     *
     * @throws IOException
     */
    private void resendWindow() throws IOException {
        printDebugErr("Resending window of " + sendWindow.size() + " packets");
        for (RxpPacket aSendWindow : sendWindow) {
            resendPacket(aSendWindow);
        }
    }

    /**
     * Send a packet. Most of the necessary fields are added in like sequence, ports, acks, and window size.
     * All packets will be added to the sending window unless they are RSTs or single ACKs.
     * If the window is full, this call will block until the packet is added to the window.
     *
     * @param packet Packet with data to send
     * @throws IOException
     */
    private void sendPacket(RxpPacket packet) throws IOException {
        packet.sequence = sequenceNum;
        packet.srcPort = srcPort;
        packet.destPort = destPort;
        packet.acknowledgement = lastAck;
        packet.ack = true;
        packet.windowSize = recvWindowSize;
        byte[] buffer = packet.getBytes();

        sequenceNum += packet.data.length;
        /* No need to keeps acks in the window */
        if (!shouldBypassWindow(packet)) {
            try {
                synchronized (sendWindow) {
                    while (sendWindow.size() >= sendWindowSize) {
                        printDebug("Send window is full: "  + sendWindowSize + ", waiting...");
                        sendWindow.wait();
                    }
                    sendWindow.add(packet);
                    updateLastSent();
                }
            } catch (InterruptedException e) {
//                e.printStackTrace();
            }
        }

        printDebug("Sending (" + state + "): " + packet);
        DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
        netEmuSocket.send(datagramPacket);
    }

    /**
     * Called by a RxpReceiver when a valid packet is received. This function is responsible for the majority of the
     * logic needed by the socket.
     *
     * When a packet is received, the window is iterated upon and outgoing packets with sequences <= the current
     * packet's acknowledgement are removed.
     *
     * If the current packet is a NACK and the acknowledgement is less than a packet's sequence,
     * the packet is retransmitted (which bypasses the sending window).
     *
     * @param packet Packet to be handled by the socket
     * @throws IOException Thrown if there are fatal connection errors
     */
    void receivePacket(RxpPacket packet) throws IOException {

        printDebug("Received (" + state + "): " + packet);
        /* We don't want to write protocol data to the stream */

        updateLastReceived();
        sendWindowSize = packet.windowSize;

        /* Iterate through send window and remove old packets. Resend NACK's packets */
        Iterator<RxpPacket> windowIt = sendWindow.iterator();
        while (windowIt.hasNext()) {
            RxpPacket item = windowIt.next();
            synchronized (sendWindow) {
                if (item.sequence + item.data.length == packet.acknowledgement) {
                    windowIt.remove();
                    sendWindow.notify();
                    break;
                } else if (item.sequence + item.data.length < packet.acknowledgement) {
                    windowIt.remove();
                    sendWindow.notify();
                } else if (item.sequence + item.data.length > packet.acknowledgement && packet.nack) {
                    printDebugErr("Received NACK for " + packet.acknowledgement);
                    resendPacket(item);
                }
            }
        }

        if (sendWindow.size() > 0 ) {
            printDebug("Send window: " + sendWindow.size());
        }

        // 1. Server: receive a SYN (handshake)
        if (packet.rst){
            throw new IOException("Connection reset");
        // Client/server: receive a reset
        } else if(state == RxpState.LISTEN || state == RxpState.SYN_SENT && packet.syn && !packet.auth){
            sendAuthenticationRequest(packet.sequence + 1);
        }
        // 2. Client: receive a SYN+ACK+AUTH (handshake)
        else if (state == RxpState.SYN_SENT || state == RxpState.AUTH_SENT
                && packet.syn && packet.ack && packet.auth){
            receiveAuthenticationRequest(packet.sequence + packet.data.length, packet.data);
        }
        // 3. Server: receive a ACK+AUTH (handshake), verify MD5 hash
        else if (state == RxpState.AUTH_SENT || state == RxpState.AUTH_SENT_1 && packet.ack && packet.auth) {
            byte[] digest = computeMD5(hash.getBytes());
            if(Arrays.equals(packet.data, digest)){
                sendAck(packet.sequence + packet.data.length);
                synchronized (connectLock) {
                    state = RxpState.ESTABLISHED;
                    connectLock.notify();
                }
            } else {
                System.err.println("Incorrect auth checksum");
                sendReset();
            }
        }
        // 4. Client: receive ACK (handshake)
        else if (state == RxpState.AUTH_SENT_1 || state == RxpState.AUTH_COMPLETED && packet.ack){
            synchronized (connectLock) {
                state = RxpState.ESTABLISHED;
                connectLock.notify();
            }
        }
        //receive a FIN
        else if (state == RxpState.ESTABLISHED && packet.fin){
            state = RxpState.CLOSE_WAIT;
            sendAck(packet.sequence + packet.data.length);
        }
        // Normal, established data packet
        else if (state == RxpState.ESTABLISHED) {

            /* Only if data is received do we want to add it to the input stream and update our sequence */
            if (packet.data.length > 0) {

                /* Make sure the sequence is what we expect */
                if (lastAck == packet.sequence){
                    /* Write to stream */
                    inputStream.received(packet.data, packet.data.length);
                    /* Only send a lone ack if we have no data to send */
                    if (outputStream.getSize() == 0) {
                        sendAck(packet.sequence + packet.data.length);
                    } else {
                        lastAck = packet.sequence + packet.data.length;
                    }
                /* This will happen if a previous packet has been dropped. Send a NACK for the packet we were expecting */
                } else if (packet.sequence > lastAck) {
                    printDebugErr("Dropping over-sequence packet (" + packet.sequence + "), expected " + lastAck);
                    sendNack(lastAck);
                /* This will happen if an old packet has been retransmitted. Send an ACK for the packet we already have */
                } else if (packet.sequence < lastAck) {
                    printDebugErr("Dropping under-sequence packet (" + packet.sequence + "), expected " + lastAck);
                    sendAck(lastAck);
                }
                //TODO: review packet and determine what data to send, if any
            }
        }
        else if (state == RxpState.FIN_WAIT_1 && packet.fin && packet.ack){
            state = RxpState.TIMED_WAIT;
            sendAck(packet.sequence + packet.data.length);
        }
        else if (state == RxpState.FIN_WAIT_1 && packet.fin){
            state = RxpState.CLOSING;
            sendAck(packet.sequence + packet.data.length);
        }
        else if (state == RxpState.FIN_WAIT_1 && packet.ack){
            state = RxpState.FIN_WAIT_2;
        }
        else if (state == RxpState.CLOSING && packet.ack){
            state = RxpState.TIMED_WAIT;
        }
        else if (state == RxpState.FIN_WAIT_2 && packet.fin){
            state = RxpState.TIMED_WAIT;
            sendAck(packet.sequence + packet.data.length);
        }
        else if (state == RxpState.LAST_ACK && packet.ack){
            close();
        }
        //TODO: established state, normal data packets and ACKs/Nacks
    }

    /**
     * Reset a connection without sending or receiving any remaining data
     *
     * @throws IOException Thrown if there are connection issues
     */
    public void reset() throws IOException {
        sendReset();
    }

    void sendSyn() throws IOException {
        RxpPacket packet = new RxpPacket(this);
        packet.syn = true;
        state = RxpState.SYN_SENT;
        sendPacket(packet);
        ++sequenceNum;
    }

    void sendAck(int ack) throws IOException {
        RxpPacket packet = new RxpPacket(this);
        packet.ack = true;
        sendPacketWithAck(packet, ack);
    }

    void sendData(byte[] data, int off, int len) throws IOException {
        //TODO: split up data into packets of size MTU and send only the number that the window allows
        //TODO: keep track of packets sent and not acked yet; maybe a queue or list of packets

        if (state == RxpState.CLOSED) {
            throw new IOException("Cannot send data because socket is closed");
        }

        RxpPacket packet = new RxpPacket(this);
        byte copy[] = new byte[len];
        System.arraycopy(data, off, copy, 0, len);
        packet.data = copy;
        sendPacket(packet);
    }

    void sendData(byte[] data, int len) throws IOException {
        sendData(data, 0, len);
    }

    void sendDataAndAck(byte[] data) throws IOException {
        RxpPacket packet = new RxpPacket(this);
        packet.data = data;
        packet.ack = true;
        sendPacket(packet);
    }

    void sendNack(int ack) throws IOException {
        RxpPacket packet = new RxpPacket(this);
        packet.nack = true;
        sendPacketWithAck(packet, ack);
    }

    void sendReset() throws IOException {
        RxpPacket packet = new RxpPacket(this);
        packet.rst = true;
        sendPacket(packet);
        close();
    }

    //received a SYN so send a SYN+ACK+AUTH
    void sendAuthenticationRequest(int acknowledgement) throws IOException {
        hash = generateString(rand,"abcdefghijklmnopqrstuvwxyz0123456789", 64);
        RxpPacket packet = new RxpPacket(this);
        packet.ack = true;
        packet.syn = true;
        packet.auth = true;
        packet.data = hash.getBytes();
        sendPacketWithAck(packet, acknowledgement);

        state = RxpState.AUTH_SENT;
    }

    //received a SYN+ACK+AUTH so send an ACK+AUTH
    void receiveAuthenticationRequest(int acknowledgement, byte[] challenge) throws IOException {
        if(state == RxpState.AUTH_SENT) //both sent a SYN at the same time
        {
            state = RxpState.AUTH_SENT_1;
        }
        else {
            state = RxpState.AUTH_COMPLETED;
        }

        byte[] digest = computeMD5(challenge);
        RxpPacket packet = new RxpPacket(this);
        packet.ack = true;
        packet.auth = true;
        packet.data = digest;
        sendPacketWithAck(packet, acknowledgement);

    }

    public boolean equals(Object other) {
        return other instanceof RxpSocket && (srcPort == ((RxpSocket) other).srcPort && destPort == ((RxpSocket) other).destPort);
    }

    private static String generateString(Random rng, String characters, int length)
    {
        char[] text = new char[length];
        for (int i = 0; i < length; i++)
        {
            text[i] = characters.charAt(rng.nextInt(characters.length()));
        }
        return new String(text);
    }

    private byte[] computeMD5(byte[] challenge){
        byte[] digest = new byte[]{};
        try{
            MessageDigest md = MessageDigest.getInstance("MD5");
            digest = md.digest(challenge);
        }catch(NoSuchAlgorithmException e) {
            printDebugErr(e.getMessage());
        }
        return digest;
    }


    /**
     * Get the outputStream for this socket. Data written to this stream is transmitted to the remote host
     *
     * @return Output stream for this socket
     */
    public OutputStream getOutputStream() {
        return outputStream;
    }

    /**
     * Get the inputstream for this socket. Data read from this stream has been received from the remote host.
     * @return Input stream for this socket
     */
    public InputStream getInputStream() {
        return inputStream;
    }


    /**
     * Receive valid packets on this socket only and pass to receivePacket()
     * If a packet is sent to a port other than the listening port, the packet is dropped.
     */
    @Override
    public void receiverStart() {
        /* Data reception loop */
        new Thread(() -> {
            receiverRun = true;
            while (receiverRun) {
                byte[] buffer = new byte[RxpSocket.UDP_MAX];
                DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
                try {
                    netEmuSocket.receive(datagramPacket);

                    RxpPacket packet = new RxpPacket(datagramPacket.getData(), datagramPacket.getLength());

                    if (packet.destPort != srcPort) {
                        printDebugErr("Dropping packet send to wrong port");
                    } else {
                        receivePacket(packet);
                    }

                } catch (IOException e) {
                    System.err.println(e.getMessage());
                    close();
                } catch (InvalidChecksumException e) {
                    printDebugErr("Dropping packet due to incorrect checksum");
                }
            }
        }).start();
    }

    @Override
    public void receiverStop() {
        receiverRun = false;
    }

    public boolean shouldBypassWindow(RxpPacket packet) {
        return (packet.ack || packet.nack || packet.rst) && packet.data.length == 0 && !(packet.syn || packet.fin || packet.auth);
    }

    public RxpState getState() {
        return state;
    }

    public short getSourcePort() {
        return srcPort;
    }

    public short getDestPort() {
        return destPort;
    }

    public InetAddress getDestination() {
        return destination;
    }

    public int getSequence() {
        return sequenceNum;
    }

    public int getLastAck() {
        return lastAck;
    }

    public short getSendWindowSize() {
        return this.sendWindowSize;
    }

    @Override
    public String toString() {
        return "RxpSocket= state: " + state.name() + " src: " + srcPort + " dest: " + destination.getHostAddress() + ":" + destPort;
    }

    public short getRecvWindowSize() {
        return recvWindowSize;
    }

    public void printDebug(String message) {
        if (debugEnabled) {
            System.out.println("[RXP] " + message);
        }
    }

    public void printDebugErr(String message) {
        if (debugEnabled) {
            System.err.println("[RXP] " + message);
        }
    }

    private void updateLastSent() {
        synchronized (timeoutLock) {
            lastSendTime = System.currentTimeMillis();
        }
    }

    private void updateLastReceived() {
        synchronized (timeoutLock) {
            lastRecvTime= System.currentTimeMillis();
        }
    }
    public void setRecvWindowSize(short size) {
        recvWindowSize = size;
    }

}
