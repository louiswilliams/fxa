import java.net.*;
import java.util.Random;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class RxpSocket {

    InetAddress address;
    RxpProtocol protocol;
    short srcPort;
    short destPort;

    int windowStart;
    int windowSize;
    byte[] buffer;
    int bufferSize;

    RxpState state;
    Random rand;

    int sequenceNum;
    String hash = "";

    public RxpSocket(RxpProtocol protocol, String hostname, short port) throws UnknownHostException {
        this.protocol = protocol;
        destPort = port;
        address = InetAddress.getByName(hostname);
        protocol.registerSocket(this);
        state = RxpState.LISTEN;
        rand = new Random();
        sequenceNum = 0; //TODO: initiate sequence number correctly

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    if (bufferSize > 0) {

                    }
                }
            }
        }).start();
    }
    
    public void close() {
    }

    public int send(byte[] toSend, int len) {
        System.arraycopy(toSend, 0, buffer, windowStart + windowSize, len);
        // TODO: Split up buffer into packets. Call protocol.sendPacket, or protocol.sendAll
        // TODO: Logic to handle window size, acking and nacking
        return len;
    }

    private void sendPacket(RxpPacket packet, int acknowledgement) {
        packet.sequence = sequenceNum;
        packet.acknowledgement = acknowledgement;

        byte[] buffer = packet.getBytes();
        send(buffer, buffer.length);
        sequenceNum += packet.data.length;
    }

    public int receive(byte[] buffer, int len) {
        return 0;
    }

    public void receivePacket(RxpPacket packet) {
        //receive a SYN (1st step of handshake)
        if(state == RxpState.LISTEN || state == RxpState.SYN_SENT && packet.syn){
            sendAuthenticationRequest(packet.sequence + packet.data.length + packet.HEADER_SIZE);
        }
        //receive a SYN+ACK+AUTH (2nd step of handshake)
        else if (state == RxpState.SYN_SENT || state == RxpState.AUTH_SENT
                && packet.syn && packet.ack && packet.auth){
            receiveAuthenticationRequest(packet.sequence + packet.data.length + packet.HEADER_SIZE, packet.data);
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

    private void sendSyn(){
        RxpPacket packet = new RxpPacket(srcPort, destPort);
        packet.syn = true;
        sendPacket(packet, -1);
    }

    private void sendAck(int acknowledgement) {
        RxpPacket packet = new RxpPacket(srcPort, destPort);
        packet.ack = true;
        sendPacket(packet, acknowledgement);
    }

    private void sendData(byte[] data){
        RxpPacket packet = new RxpPacket(srcPort, destPort);
        packet.data = data;
        sendPacket(packet, -1);
    }

    private void sendDataAndAck(byte[] data, int acknowledgment) {
        RxpPacket packet = new RxpPacket(srcPort, destPort);
        packet.data = data;
        packet.ack = true;
        sendPacket(packet, acknowledgment);
    }

    private void sendNack(int acknowledgment) {
        RxpPacket packet = new RxpPacket(srcPort, destPort);
        packet.nack = true;
        sendPacket(packet, acknowledgment);
    }

    private void sendReset() {
        RxpPacket packet = new RxpPacket(srcPort, destPort);
        packet.rst = true;

        byte[] buffer = packet.getBytes();
        send(buffer, buffer.length);
        state = RxpState.CLOSED;
    }

    //received a SYN so send a SYN+ACK+AUTH
    private void sendAuthenticationRequest(int acknowledgement) {
        hash = generateString(rand,"abcdefghijklmnopqrstuvwxyz0123456789", 64);
        RxpPacket packet = new RxpPacket(srcPort, destPort);
        packet.ack = true;
        packet.syn = true;
        packet.auth = true;
        packet.data = hash.getBytes();
        sendPacket(packet, acknowledgement);

        state = RxpState.AUTH_SENT;
    }

    //received a SYN+ACK+AUTH so send an ACK+AUTH
    private void receiveAuthenticationRequest(int acknowledgement, byte[] challenge) {
        byte[] digest = computeMD5(challenge);

        RxpPacket packet = new RxpPacket(srcPort, destPort);
        packet.ack = true;
        packet.auth = true;
        packet.data = digest;
        sendPacket(packet, acknowledgement);

        if(state == RxpState.AUTH_SENT) //both sent a SYN at the same time
            state = RxpState.AUTH_SENT_1;
        else
            state = RxpState.AUTH_COMPLETED;
    }

    public boolean equals(Object other) {
        return (address.equals(((RxpSocket)other).address) && srcPort == ((RxpSocket)other).srcPort
                && destPort == ((RxpSocket)other).destPort);
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

}
