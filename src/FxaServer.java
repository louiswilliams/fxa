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

    public static void main(String[] args) throws SocketException, UnknownHostException {

        if (args.length != 3) {
            System.err.println(usage);
            throw new IllegalArgumentException("Incorrect parameters");
        }

        port = Short.parseShort(args[0]);
        //TODO: make sure port is valid and even
        try {
            netEmuAddress = InetAddress.getByName(args[1]);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            exitWithError("Unknown host: " + args[1]);
        }
        netEmuPort = Short.parseShort(args[2]);
        //TODO: make sure port is valid

        Scanner sc = new Scanner(System.in);

        DatagramSocket netEmu = new DatagramSocket(port);
        netEmu.connect(netEmuAddress, netEmuPort);

        RxpServerSocket serverSocket = new RxpServerSocket(netEmu, port);

        RxpSocket client = serverSocket.accept();
        System.out.println("Accepted!");
        OutputStream outputStream = client.getOutputStream();
        InputStream inputStream = client.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

        System.out.println("Reading: ");
        while (true) {
            try {
                int b = inputStream.read();
                System.out.print(String.valueOf((char) b));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


//        while(true){
//            String command = sc.nextLine();
//            if(command.equals("terminate")){
//                //TODO
//                break;
//            } else if (command.split(" ")[0].equals("window")){
//                //TODO
//            } else {
//                System.out.println("Not a valid command.");
//            }
//        }
    }

    private static void exitWithError(String message) {
        System.err.println(message);
        System.exit(1);
    }
}
