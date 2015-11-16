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

    //TODO: how are we keeping track of sequence numbers?
    int sequenceNum;
    String challenge = "";

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

    private void sendPacket(RxpPacket packet, int sequence, int acknowledgement) {
        packet.sequence = sequence;
        packet.acknowledgement = acknowledgement;

        byte[] buffer = packet.getBytes();
        send(buffer, buffer.length);
        sequenceNum += buffer.length;
    }

    public int receive(byte[] buffer, int len) {
        return 0;
    }

    public void receivePacket(RxpPacket packet) {
        //TODO: check state and proceed accordingly
        if(state == RxpState.LISTEN || state == RxpState.SYN_SENT && packet.syn){
            sendAuthenticationRequest(sequenceNum, packet.sequence + packet.data.length + packet.HEADER_SIZE);
        } else if (state == RxpState.SYN_SENT && packet.syn && packet.ack && packet.auth){
            if(packet.data.equals(challenge.getBytes())){
                sendAck(sequenceNum, packet.sequence + packet.data.length + packet.HEADER_SIZE);
            }
        }
    }

//    public void reset() throws IOException {
//
//    }
    private void sendSyn(){
        RxpPacket packet = new RxpPacket(srcPort, destPort);
        packet.syn = true;
        sendPacket(packet, sequenceNum, -1);
    }

    private void sendAck(int sequence, int acknowledgement) {
        RxpPacket packet = new RxpPacket(srcPort, destPort);
        packet.ack = true;
        sendPacket(packet, sequence, acknowledgement);
    }

    private void sendData(byte[] data, int sequence){
        RxpPacket packet = new RxpPacket(srcPort, destPort);
        packet.data = data;
        sendPacket(packet, sequence, -1);
    }

    private void sendDataAndAck(byte[] data, int sequence, int acknowledgment) {
        RxpPacket packet = new RxpPacket(srcPort, destPort);
        packet.data = data;
        packet.ack = true;
        sendPacket(packet, sequence, acknowledgment);
    }

    private void sendNack(int sequence, int acknowledgement) {
        RxpPacket packet = new RxpPacket(srcPort, destPort);
        packet.nack = true;
        packet.sequence = sequence;
        packet.acknowledgement = acknowledgement;

        byte[] buffer = packet.getBytes();
        send(buffer, buffer.length);
    }

    private void sendReset() {
        RxpPacket packet = new RxpPacket(srcPort, destPort);
        packet.rst = true;

        byte[] buffer = packet.getBytes();
        send(buffer, buffer.length);
        state = RxpState.CLOSED;
    }

    //received a SYN so send a SYN+ACK+AUTH
    private void sendAuthenticationRequest(int sequence, int acknowledgement) {
        challenge = generateString(rand,"abcdefghijklmnopqrstuvwxyz0123456789", 64);
        RxpPacket packet = new RxpPacket(srcPort, destPort);
        packet.ack = true;
        packet.syn = true;
        packet.auth = true;
        packet.data = challenge.getBytes();
        sendPacket(packet, sequence, acknowledgement);

        state = RxpState.AUTH_SENT;
    }

    //received a SYN+ACK+AUTH so send an ACK+AUTH
    private void receiveAuthenticationRequest(int sequence, int acknowledgement, String challenge) {
        byte[] digest = new byte[]{};
        try{
            MessageDigest md = MessageDigest.getInstance("MD5");
            digest = md.digest(challenge.getBytes());
        }catch(NoSuchAlgorithmException e) {
            System.err.println(e.getMessage());
        }

        RxpPacket packet = new RxpPacket(srcPort, destPort);
        packet.ack = true;
        packet.auth = true;
        packet.data = digest;
        sendPacket(packet, sequence, acknowledgement);

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

}
