import javax.xml.crypto.Data;
import java.io.*;
import java.net.*;
import java.util.Objects;
import java.util.Random;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class RxpSocket implements RxpReceiver {

    DatagramSocket netEmuSocket;

    short srcPort;
    short destPort;

    InetAddress destination;

    private int windowStart;
    private int windowSize;
    private byte[] buffer;
    private int bufferSize;

    public static final int MTU = 1500;

    private RxpState state;
    private Random rand;

    private int lastAck;
    private int sequenceNum;
    private String hash = "";

    private RxpInputStream inputStream;
    private RxpOutputStream outputStream;

    RxpServerSocket serverSocket;
    RxpReceiver dataReceiver;

    boolean receiverRun = true;


    public RxpSocket(DatagramSocket netEmuSocket, InetAddress dest, short destPort) {
        Random r = new Random();
        this.netEmuSocket = netEmuSocket;
        this.destPort = destPort;
        srcPort = (short) (r.nextInt(32767 - 1024) + 1024);
        destination = dest;
        state = RxpState.LISTEN;
        rand = new Random();
        sequenceNum = 0; //TODO: initiate sequence number correctly

        inputStream = new RxpInputStream(this);
        outputStream = new RxpOutputStream(this);
        dataReceiver = this;
    }

    public RxpSocket(DatagramSocket netEmuSocket, InetAddress dest, short port, RxpReceiver dataReceiver) {
        this(netEmuSocket, dest, port);
        this.dataReceiver = dataReceiver;
    }

    public void connect() throws IOException {
        System.out.println("Connecting");
        receiverStart();
        sendSyn();
    }

    public void close() {
        receiverStop();
        inputStream.close();
        outputStream.close();
    }

    private void sendPacketWithAck(RxpPacket packet, int ack) throws IOException {
        lastAck = ack;
        sendPacket(packet);
    }

    private void sendPacket(RxpPacket packet) throws IOException {
        packet.sequence = sequenceNum;
        packet.srcPort = srcPort;
        packet.destPort = destPort;
        packet.acknowledgement = lastAck;
        packet.ack = true;

        byte[] buffer = packet.getBytes();
        sequenceNum += buffer.length;
        DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
        netEmuSocket.send(datagramPacket);
    }

    public void receivePacket(RxpPacket packet) throws IOException {

        inputStream.received(packet.data, packet.data.length);

        //receive a SYN (1st step of handshake)
        if(state == RxpState.LISTEN || state == RxpState.SYN_SENT && packet.syn){
            sendAuthenticationRequest(packet.sequence + packet.data.length);
        }
        //receive a SYN+ACK+AUTH (2nd step of handshake)
        else if (state == RxpState.SYN_SENT || state == RxpState.AUTH_SENT
                && packet.syn && packet.ack && packet.auth){
            receiveAuthenticationRequest(packet.sequence + packet.data.length, packet.data);
        }
        //receive a ACK+AUTH (3rd step of handshake), verify MD5 hash
        else if (state == RxpState.AUTH_SENT || state == RxpState.AUTH_SENT_1 && packet.ack && packet.auth) {
            byte[] digest = computeMD5(hash.getBytes());
            if(packet.data.equals(digest)){
                sendAck(packet.sequence + packet.data.length + packet.HEADER_SIZE);
                state = RxpState.ESTABLISHED;
            } else {
                sendReset();
                state = RxpState.LISTEN;
            }
        }
        //receive ACK (4th step of handshake)
        else if (state == RxpState.AUTH_SENT_1 || state == RxpState.AUTH_COMPLETED && packet.ack){
            state = RxpState.ESTABLISHED;
        }
        //receive a reset
        else if (packet.rst){
            state = RxpState.CLOSED;
        }
        //receive a FIN
        else if (state == RxpState.ESTABLISHED && packet.fin){
            sendAck(packet.sequence + packet.data.length + packet.HEADER_SIZE);
            state = RxpState.CLOSE_WAIT;
        }
        else if (state == RxpState.FIN_WAIT_1 && packet.fin && packet.ack){
            sendAck(packet.sequence + packet.data.length + packet.HEADER_SIZE);
            state = RxpState.TIMED_WAIT;
        }
        else if (state == RxpState.FIN_WAIT_1 && packet.fin){
            sendAck(packet.sequence + packet.data.length + packet.HEADER_SIZE);
            state = RxpState.CLOSING;
        }
        else if (state == RxpState.FIN_WAIT_1 && packet.ack){
            state = RxpState.FIN_WAIT_2;
        }
        else if (state == RxpState.CLOSING && packet.ack){
            state = RxpState.TIMED_WAIT;
        }
        else if (state == RxpState.FIN_WAIT_2 && packet.fin){
            sendAck(packet.sequence + packet.data.length + packet.HEADER_SIZE);
            state = RxpState.TIMED_WAIT;
        }
        else if (state == RxpState.LAST_ACK && packet.ack){
            state = RxpState.CLOSED;
        }
        //TODO: established state, normal data packets and ACKs/Nacks
    }

//    public void reset() throws IOException {
//
//    }

    private void sendSyn() throws IOException {
        RxpPacket packet = new RxpPacket(srcPort, destPort);
        packet.syn = true;
        sendPacket(packet);
    }

    private void sendAck(int ack) throws IOException {
        RxpPacket packet = new RxpPacket(srcPort, destPort);
        packet.ack = true;
        sendPacketWithAck(packet, ack);
    }

    void sendData(byte[] data, int len) throws IOException {
        RxpPacket packet = new RxpPacket(srcPort, destPort);
        byte copy[] = new byte[len];
        System.arraycopy(data, 0, copy, 0, len);
        packet.data = copy;
        sendPacket(packet);
    }

    private void sendDataAndAck(byte[] data) throws IOException {
        RxpPacket packet = new RxpPacket(srcPort, destPort);
        packet.data = data;
        packet.ack = true;
        sendPacket(packet);
    }

    private void sendNack() throws IOException {
        RxpPacket packet = new RxpPacket(srcPort, destPort);
        packet.nack = true;
        sendPacket(packet);
    }

    private void sendReset() throws IOException {
        RxpPacket packet = new RxpPacket(srcPort, destPort);
        packet.rst = true;

        sendPacket(packet);
        state = RxpState.CLOSED;
    }

    //received a SYN so send a SYN+ACK+AUTH
    private void sendAuthenticationRequest(int acknowledgement) throws IOException {
        hash = generateString(rand,"abcdefghijklmnopqrstuvwxyz0123456789", 64);
        RxpPacket packet = new RxpPacket(srcPort, destPort);
        packet.ack = true;
        packet.syn = true;
        packet.auth = true;
        packet.data = hash.getBytes();
        sendPacketWithAck(packet, acknowledgement);

        state = RxpState.AUTH_SENT;
    }

    //received a SYN+ACK+AUTH so send an ACK+AUTH
    private void receiveAuthenticationRequest(int acknowledgement, byte[] challenge) throws IOException {
        byte[] digest = computeMD5(challenge);

        RxpPacket packet = new RxpPacket(srcPort, destPort);
        packet.ack = true;
        packet.auth = true;
        packet.data = digest;
        sendPacket(packet);

        if(state == RxpState.AUTH_SENT) //both sent a SYN at the same time
            state = RxpState.AUTH_SENT_1;
        else
            state = RxpState.AUTH_COMPLETED;
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
            System.err.println(e.getMessage());
        }
        return digest;
    }


    public OutputStream getOutputStream() {
        return outputStream;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    @Override
    public void receiverStart() {
        /* Data reception loop */
        new Thread(() -> {
            receiverRun = true;
            while (receiverRun) {
                byte[] buffer = new byte[MTU];
                DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
                try {
                    netEmuSocket.receive(datagramPacket);

                    RxpPacket packet = new RxpPacket(datagramPacket.getData(), datagramPacket.getLength());

                    if (packet.destPort != srcPort) {
                        throw new IOException("Received on wrong port!");
                    }

                    receivePacket(packet);
                } catch (IOException e) {
                    System.err.println(e.getMessage());
                } catch (InvalidChecksumException e) {
                    System.err.println("Dropping packet due to incorrect checksum");
                }
            }
        }).start();
    }

    @Override
    public void receiverStop() {
        receiverRun = false;
    }
}
