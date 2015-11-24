import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ArrayBlockingQueue;

public class RxpInputStream extends InputStream implements DataListener {

    final private byte buffer[];
    int cursor;
    int size;

    public RxpInputStream() {

        buffer = new byte[100 * RxpSocket.MTU];

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
                while (size == 0)
                    buffer.wait();

                b = buffer[cursor++];
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
                while (size == buffer.length)
                    buffer.wait();

                cursor = cursor + 1 % buffer.length;
                buffer[cursor] = data;
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

    public byte[] getBuffer(){
        return buffer;
    }
}
