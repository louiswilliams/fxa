import com.sun.istack.internal.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RxpOutputStream extends OutputStream {

    final byte buffer[];
    int size;
    RxpSocket socket;
    boolean run;
    boolean flush;
    Thread writer;

    public RxpOutputStream(RxpSocket socket) {
        this.socket = socket;

        buffer = new byte[100 * RxpSocket.MTU];

        writer = new Thread(() -> {
            run = true;
            while (run) {
                try {
                    synchronized (buffer) {
                        /* Wait to fill buffer or flush is called */
                        while (size != buffer.length && !flush) {
                            buffer.wait();
                        }
                        socket.sendData(buffer, size);
                        size = 0;
                        buffer.notify();
                        if (flush) {
                            flush = false;
                        }
                    }
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            }
        });

        writer.start();
    }

    @Override
    public void write(int in) throws IOException {
        byte b = (byte) (in & 0xff); // Only read the lower 8 bits

        try {
            synchronized (buffer) {
                while (size == buffer.length) {
                    buffer.wait();
                }
                buffer[size++] = b;
                if (size == buffer.length) {
                    buffer.notify();
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void write(byte b[], int off, int len) throws IOException {
        for (int i = off; i < len; i++) {
            write(b[i]);
        }
        flush();
    }

    @Override
    public void flush() {
        synchronized (buffer) {
            flush = true;
            buffer.notify();
        }
    }

    @Override
    public void close() {
        run = false;
    }
}
