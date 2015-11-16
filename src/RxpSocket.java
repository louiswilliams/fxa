import java.io.IOException;
import java.net.*;

public class RxpSocket {

    InetAddress address;
    RxpProtocol protocol;
    short srcPort;
    short destPort;


    public RxpSocket(RxpProtocol protocol, String hostname, short port) throws UnknownHostException {
        this.protocol = protocol;
        destPort = port;
        address = InetAddress.getByName(hostname);
    }
    
    public void close() {

    }

    public int send(byte[] buffer, int len) {
        return 0;
    }

    public int receive(byte[] buffer, int len) {
        return 0;
    }

    public  void reset() throws IOException {
        RxpPacket packet = new RxpPacket(srcPort, destPort);
        packet.rst = true;
    }

}
