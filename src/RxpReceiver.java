/**
 * Used by a RxpSocket to start receiving packets on the transport layer to pass into a socket.
 * This is useful so an RxpServerSocket can read from a datagram socket and pass packets to the correct clients,
 * and a RxpSocket that is not part of a server can handle its own packets
 */
public interface RxpReceiver {
    /**
     * Start a thread to receive packets on the datagram transport layer.
     * This function should call RxpSocket.receivePacket() on the receiving socket.
     * This
     */
    void receiverStart();

    /**
     * Stop receiving packets, used after closing a connection.
     */
    void receiverStop();

}
