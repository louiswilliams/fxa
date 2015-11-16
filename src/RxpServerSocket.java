import java.net.DatagramSocket;
import java.net.SocketException;

public class RxpServerSocket {

    RxpSocket socket;

    public RxpServerSocket(int port) throws SocketException {
        socket = new RxpSocket(null, port);
    }

    public RxpSocket accept() {
        return null;
    }
}
