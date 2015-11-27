/**
 * Used by a RxpSocket to start receiving packets and buffer into an InputStream
 */
public interface RxpReceiver {
    /**
     * Start a thread to receive packets
     */
    void receiverStart();

    /**
     * Stop receiving packets, used after closing a connection
     */
    void receiverStop();

    void connectionClosed(RxpSocket socket);

}
