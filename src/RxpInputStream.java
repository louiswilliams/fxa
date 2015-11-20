import java.io.IOException;
import java.io.InputStream;

public class RxpInputStream extends InputStream implements DataListener {

    private byte[] buffer;
    private int cursor;
    private int available;

    public RxpInputStream(RxpProtocol protocol, RxpSocket socket) {
        protocol.addInputStreamListener(socket, this);
    }

    @Override
    public int available() {
        return available;
    }

    @Override
    public int read() throws IOException {
        return 0;
    }


    /* Data listener */
    @Override
    public void received(byte[] data, int len) {

    }
}
