import java.io.IOException;
import java.io.OutputStream;

public class RxpOutputStream extends OutputStream {

    final byte buffer[];
    int size;
    int start;
    RxpSocket socket;
    boolean run;
    boolean flush;
    Thread writer;

    public RxpOutputStream(RxpSocket socket) {
        this(socket, RxpSocket.MSS);
    }

    public RxpOutputStream(RxpSocket socket, int capacity) {
        this.socket = socket;

        buffer = new byte[capacity];

        writer = new Thread(() -> {
            run = true;
            while (run) {
                try {
                    byte[] output;
                    synchronized (buffer) {
                        /* Wait to fill a MSS or flush is called */
                        while (size < RxpSocket.MSS && !flush) {
                            buffer.wait();
                        }
                        int datalen = Math.min(size, RxpSocket.MSS);

                        output = new byte[datalen];

                        if (start + size > buffer.length) {
                            int j = start;
                            for (int i = 0; i < datalen; i++) {
                                output[i] = buffer[j];
                                j = (j + 1) % buffer.length;
                            }
                        } else {
                            System.arraycopy(buffer, start, output, 0, datalen);
                        }

                        /* Keep sending until no data left*/
                        if (flush && datalen < RxpSocket.MSS) {
                            flush = false;
                        }
                        start = (start + datalen) % buffer.length;
                        size -= datalen;
                        buffer.notify();
                    }
                    socket.sendData(output, output.length);

                } catch (InterruptedException | IOException e) {
                    run = false;
                }
            }
        });

        writer.start();
    }

    public int getSize() {
        int size;
        synchronized (buffer) {
            size = this.size;
        }
        return size;
    }

    @Override
    public void write(int in) throws IOException {
        byte b = (byte) (in & 0xff); // Only read the lower 8 bits

        if (socket.getState() == RxpState.CLOSED || !run) {
            throw new IOException("Cannot write because socket is closed");
        }
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
        writer.interrupt();
    }
}
