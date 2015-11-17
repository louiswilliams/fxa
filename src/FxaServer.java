import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Scanner;

public class FxaServer {

    public static short port;
    public static InetAddress netEmuAddress;
    public static short netEmuPort;

    public static void main(String[] args) {

        if (args.length != 3)
            throw new IllegalArgumentException("Incorrect parameters");

        port = Short.parseShort(args[0]);
        //TODO: make sure port is valid and even
        try {
            netEmuAddress = InetAddress.getByAddress(args[1].getBytes());
        } catch (UnknownHostException e) {
            e.printStackTrace();
            System.out.println("Unknown host.");
        }
        netEmuPort = Short.parseShort(args[2]);
        //TODO: make sure port is valid

        Scanner sc = new Scanner(System.in);

        while(true){
            String command = sc.nextLine();
            if(command.equals("terminate")){
                //TODO
                break;
            } else if (command.split(" ")[0].equals("window")){
                //TODO
            } else {
                System.out.println("Not a valid command.");
            }
        }
    }

}
