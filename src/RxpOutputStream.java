/**
 * RxpOutputStream
 *
 * Buffers data from the client socket and writes out to the socket when enough data has been written or flush() has
 * been called
 */
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

    /**
     * Create an RxpOutputStream for the given socket, which data will be written to. The buffer size will be one MSS
     *
     * @param socket Socket to write data to
     */
    public RxpOutputStream(RxpSocket socket) {
        this(socket, RxpSocket.MSS);
    }

    /**
     * Create an RxpOutputStream for a socket with a given capacity in bytes.
     *
     * When the buffer size has reached one MSS, an MSS of data is sent to the socket.
     * If flush() has been called, data will be sent in blocks of at most one MSS until the buffer has emptied.
     *
     * @param socket Socket to send data to
     * @param capacity Capacity, in bytes, of the buffer
     */
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

    /**
     * Get the buffer size
     *
     * @return Number of bytes in the buffer
     */
    public int getSize() {
        int size;
        synchronized (buffer) {
            size = this.size;
        }
        return size;
    }

    /**
     * Write one byte to the output stream. Blocks if the buffer is full, and triggers a write out to the socket
     * if the size reaches one MSS
     *
     * @param in Byte of data
     * @throws IOException Thrown if the connection has been closed
     */
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

    /**
     * Write len bytes from b starting from off. Implicitly calls flush() to the data can be written out faster.
     *
     * @param b Byte array of data
     * @param off Start offset to write
     * @param len Length of data to write
     * @throws IOException Thrown if the connection has been closed
     */
    @Override
    public void write(byte b[], int off, int len) throws IOException {
        for (int i = off; i < len; i++) {
            write(b[i]);
        }
        flush();
    }

    /**
     * Request data to be written out to the socket even if the buffer size has not reached one MSS. If the buffer size
     * is greater than one MSS, the entire buffer is sent until it is empty.
     *
     */
    @Override
    public void flush() {
        synchronized (buffer) {
            flush = true;
            buffer.notify();
        }
    }

    /**
     * Interrupt the writer and cause it stop runningvo
     */
    @Override
    public void close() {
        writer.interrupt();
    }
}
