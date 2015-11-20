import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.UnknownHostException;

public class RxpServerSocket {

    RxpSocket socket;

    public RxpServerSocket(RxpProtocol rxpProtocol, short port) throws SocketException, UnknownHostException {
        socket = new RxpSocket(rxpProtocol, port);
    }

    public RxpSocket accept() {
        return null;
    }
}
