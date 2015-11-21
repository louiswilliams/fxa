import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ArrayBlockingQueue;

public class RxpInputStream extends InputStream implements DataListener {

    private final ArrayBlockingQueue<Byte> buffer;
    private RxpSocket socket;

    public RxpInputStream(RxpSocket s) {
        this.socket = s;

        buffer = new ArrayBlockingQueue<>(100 * RxpSocket.MTU);
    }

    @Override
    public int available() {
        return buffer.size();
    }

    @Override
    public int read() throws IOException {
        int b = - 1;
        try {
            b = buffer.take();
        } catch (InterruptedException e) {
            e.printStackTrace();

        }
        return b;
    }

    @Override
    public void close() {
//        protocol.removeInputStreamListener(socket);
    }

    @Override
    public void received(byte data) {
        try {
            buffer.put(data);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /* Data listener */
    @Override
    public void received(byte[] data, int len) {
        for (int i = 0; i < len; i++) {
            received(data[i]);
        }
    }
}
