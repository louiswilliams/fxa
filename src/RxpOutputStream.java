import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RxpOutputStream extends OutputStream {

    final byte buffer[];
    int size;
    int start;
    RxpSocket socket;
    boolean run;
    boolean flush;
    Thread writer;

    public RxpOutputStream(RxpSocket socket) {
        this.socket = socket;

        buffer = new byte[100 * RxpSocket.MSS];

        writer = new Thread(() -> {
            run = true;
            while (run) {
                try {
                    synchronized (buffer) {
                        /* Wait to fill a MSS or flush is called */
                        while (size < RxpSocket.MSS && !flush) {
                            buffer.wait();
                        }
                        int datalen = Math.min(size, RxpSocket.MSS);

                        if (start + size > buffer.length) {
                            byte[] output = new byte[datalen];
                            int j = start;
                            for (int i = 0; i < datalen; i++) {
                                output[i] = buffer[j];
                                j = (j + 1) % buffer.length;
                            }
                            socket.sendData(output, datalen);
                        } else {
                            socket.sendData(buffer, start, datalen);
                        }

                        /* Keep sending until no data left*/
                        if (flush && datalen < RxpSocket.MSS) {
                            flush = false;
                        }
                        start = (start + datalen) % buffer.length;
                        size -= datalen;
                        buffer.notify();
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
                int i = (start + size) % buffer.length;
                buffer[i] = b;
                size++;
                if (size == RxpSocket.MSS) {
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
