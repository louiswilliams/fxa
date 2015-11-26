import java.io.IOException;
import java.io.InputStream;

public class RxpInputStream extends InputStream implements DataListener {

    final private byte buffer[];
    int cursor;
    int size;
    boolean closed;

    public RxpInputStream() {
        this(RxpSocket.MSS);
    }

    public RxpInputStream(int capacity) {
        buffer = new byte[capacity];
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
                    buffer.wait();
                    if (closed) {
                        throw new IOException("Socket closed");
                    }
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
        closed = true;
        synchronized (buffer) {
            buffer.notify();
        }
    }

    @Override
    public void received(byte data) throws IOException {
        try {
            synchronized (buffer) {
                while (size == buffer.length) {
                    buffer.wait();
                    if (closed) {
                        throw new IOException("Socket closed");
                    }
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
    public void received(byte[] data, int len) throws IOException {
        for (int i = 0; i < len; i++) {
            received(data[i]);
        }
    }
}
