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

    private static FxaFileTransfer fileTransfer;

    static final String usage = "FxaServer PORT NET_EMU_ADDRESS NET_EMU_PORT";

    public static void main(String[] args) throws SocketException, UnknownHostException {

        if (args.length != 3) {
            System.err.println(usage);
            throw new IllegalArgumentException("Incorrect parameters");
        }

        if(!isValidPort(Short.parseShort(args[0])))
            throw new IllegalArgumentException("First port number is invalid.");
        else
            port = Short.parseShort(args[0]);

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

        new Thread(() -> {
            while (true) {
                RxpSocket socket = serverSocket.accept();
                FxaFileTransfer fileTransfer = new FxaFileTransfer(socket);
                System.out.println("In thread of FxaServer");
                fileTransfer.serve();
                System.out.println("after serve called");
            }
        }).start();

        while(true) {
            String command = keyboard.nextLine();
            String[] parts = command.split(" ");

            if (command.equals("terminate")) {
                serverSocket.close();
                break;
                //TODO: make sure the connection closed correctly at both ends
            } else if (parts[0].equals("window") && parts.length == 2) {
                try {
                    short size = Short.parseShort(parts[1]);
                    if (size > 0) {
                        serverSocket.setWindowSize(size);
                    } else
                        System.out.println("Window size must be a positive integer.");
                } catch (NumberFormatException e) {
                    System.out.println("Not a valid window size.");
                }
                //TODO: make sure this works
            } else {
                System.out.println("Not a valid command.");
            }
        }

        System.exit(0);
    }

    private static void exitWithError(String message) {
        System.err.println(message);
        System.exit(1);
    }

    private static boolean isValidPort(short port){
        return(port>1 && port<65535 && port%2 == 1);
    }
}
