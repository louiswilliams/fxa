/**
 * Used to buffer incoming data from an RxpSocket to be read by a client.
 */
import java.io.IOException;
import java.io.InputStream;

public class RxpInputStream extends InputStream{

    final private byte buffer[];
    int cursor;
    int size;
    boolean closed;

    /**
     * Create an InputStream with the default buffer size of one MSS
     */
    public RxpInputStream() {
        this(RxpSocket.MSS);
    }

    /**
     * Create an InputStream with a fixed capacity
     *
     * @param capacity Number of bytes of buffer space
     */
    public RxpInputStream(int capacity) {
        buffer = new byte[capacity];
    }

    /**
     * Bytes available
     *
     * @return Number of bytes in the buffer that can be read
     */
    @Override
    public int available() {
        return size;
    }

    /**
     * Read one byte from the buffer. Blocks if the buffer is empty
     *
     * @return Single byte, or -1 if the end of input has been reached.
     * @throws IOException If the socket has been closed
     */
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

    /**
     * Close the input stream, which will cause subsequent read()s to throw IOExceptions
     */
    @Override
    public void close() {
        closed = true;
        synchronized (buffer) {
            buffer.notify();
        }
    }

    /**
     * Add one byte to the buffer. Blocks if the buffer is full
     *
     * @param data Byte of data
     * @throws IOException Thrown if the connection has been closed
     */
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

    /**
     * Add len bytes to the buffer. Blocks if the buffer is full
     *
     * @param data Byte of data
     * @param len Length of data to write to the buffer
     * @throws IOException Thrown if the connection has been closed
     */
    public void received(byte[] data, int len) throws IOException {
        for (int i = 0; i < len; i++) {
            received(data[i]);
        }
    }

    public int getSize() {
        int size;
        synchronized (buffer) {
            size = this.size;
        }
        return size;
    }
}
