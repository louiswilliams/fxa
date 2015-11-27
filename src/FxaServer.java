import java.io.*;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Scanner;

public class FxaServer {

    public static short port;
    public static InetAddress netEmuAddress;
    public static short netEmuPort;

    static final String usage = "FxaServer PORT NET_EMU_ADDRESS NET_EMU_PORT";

    /**
     * Server for the FxA application. Handles the following commands: window W
     * (changes the receiving window size to W); terminate (disconnects all client connections)
     *
     * @param args X: the port number at which the FxA-client’s UDP socket should bind to (even number and equal to the server’s port number minus 1.)
     *             A: the IP address of NetEmu
     *             P: the UDP port number of NetEmu
     * @throws SocketException
     * @throws UnknownHostException
     */
    public static void main(String[] args) throws SocketException, UnknownHostException {

        if (args.length != 3) {
            System.err.println(usage);
            throw new IllegalArgumentException("Incorrect parameters");
        }

        port = Short.parseShort(args[0]);
        if(!isValidPort(port) || port%2 == 0)
            throw new IllegalArgumentException("First port number is invalid.");

        try {
            netEmuAddress = InetAddress.getByName(args[1]);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            exitWithError("Unknown host: " + args[1]);
        }

        netEmuPort = Short.parseShort(args[2]);

        Scanner keyboard = new Scanner(System.in);

        DatagramSocket netEmu = new DatagramSocket(port);
        netEmu.connect(netEmuAddress, netEmuPort);

        RxpServerSocket serverSocket = new RxpServerSocket(netEmu, port);

        //thread to handle socket connections and file transfers
        new Thread(() -> {
            while (true) {
                try {
                    RxpSocket socket = serverSocket.accept();
                    socket.resetInputStream();
                    socket.resetOutputStream();
                    FxaFileTransfer fileTransfer = new FxaFileTransfer(socket);
                    fileTransfer.serve();
                } catch (IOException e) {
                    System.err.println("Could not accept: " + e.getMessage());
                }
            }
        }).start();

        //listens to the command line for new commands
        while(true) {
            String command = keyboard.nextLine();
            String[] parts = command.split(" ");

            if (command.equals("terminate")) {
                try {
                    serverSocket.close();
                } catch (IOException e){
                    System.err.println(e.getMessage());
                }
                break;
            } else if (parts[0].equals("window") && parts.length == 2) {
                try {
                    short size = Short.parseShort(parts[1]);
                    if (size > 0) {
                        serverSocket.setWindowSize(size);
                        System.out.println("Server window size set to " + serverSocket.getWindowSize());
                    } else
                        System.out.println("Window size must be a positive integer.");
                } catch (NumberFormatException e) {
                    System.out.println("Not a valid window size.");
                }
            } else {
                System.out.println("Not a valid command.");
            }
        }
    }

    /**
     * Prints an error message and exits application.
     *
     * @param message the error message
     */
    private static void exitWithError(String message) {
        System.err.println(message);
        System.exit(1);
    }

    /**
     * Checks if a port is valid.
     *
     * @param port Port to be tested
     * @return true if the port is valid
     */
    private static boolean isValidPort(short port){
        return(port>1 && port<65535);
    }
}
