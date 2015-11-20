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

        RxpProtocol rxp = new RxpProtocol(netEmuAddress, netEmuPort);
        RxpSocket socket = new RxpSocket(rxp, port);

        String helloMessage = "Hello message!";
        socket.send(helloMessage.getBytes(), helloMessage.length());

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
