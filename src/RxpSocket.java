import java.io.IOException;
import java.net.*;

public class RxpSocket {

    InetAddress address;
    RxpProtocol protocol;
    short srcPort;
    short destPort;

    int windowStart;
    int windowSize;
    byte[] buffer;
    int bufferSize;


    public RxpSocket(RxpProtocol protocol, String hostname, short port) throws UnknownHostException {
        this.protocol = protocol;
        destPort = port;
        address = InetAddress.getByName(hostname);
        protocol.registerSocket(this);

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

    public int receive(byte[] buffer, int len) {
        return 0;
    }

    public  void reset() throws IOException {
        RxpPacket packet = new RxpPacket(srcPort, destPort);
        packet.rst = true;
    }

    private void sendAck(int sequence, int acknowledgement) {
        RxpPacket packet = new RxpPacket(srcPort, destPort);
        packet.ack = true;
        packet.sequence = sequence;
        packet.acknowledgement = acknowledgement;

        byte[] buffer = packet.getBytes();
        send(buffer, buffer.length);
    }

    private void sendNack(int sequence, int acknowledgement) {

    }

    private void sendReset() {

    }

    private void authenticateClient() {

    }

    private void receiveAuthentication() {

    }

    public boolean equals(Object other) {
        // TODO: Later
        return true;
    }
}
