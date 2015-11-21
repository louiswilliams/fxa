public interface DataListener {

    void received(byte data);
    void received(byte[] data, int len);
}
