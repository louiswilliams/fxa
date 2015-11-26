import java.io.IOException;

public interface DataListener {

    void received(byte data) throws IOException;
    void received(byte[] data, int len) throws IOException;
}
