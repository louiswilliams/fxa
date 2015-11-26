import java.io.IOException;
import java.io.InputStream;

public class RxpInputStream extends InputStream implements DataListener {

    final private byte buffer[];
    int cursor;
    int size;

    public RxpInputStream() {

        buffer = new byte[100 * RxpSocket.MSS];
    }

    @Override
    public int available() {
        return size;
    }

    @Override
    public int read() throws IOException {
        int b = - 1;
        try {
            synchronized (buffer) {
                while (size == 0) {
                    System.out.println("InputStream buffer is empty, waiting for data to be added");
                    buffer.wait();
                }

                b = ((int)buffer[cursor]) & 0xFF; // Convert to a signed value
                cursor = (cursor + 1) % buffer.length;
                --size;
                buffer.notify();
            }
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
            synchronized (buffer) {
                while (size == buffer.length) {
//                    System.out.println("InputStream buffer is full, waiting for data to be read");
                    buffer.wait();
                }

                int i = (cursor + size) % buffer.length;
                buffer[i] = data;
                ++size;
                buffer.notify();
            }
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
