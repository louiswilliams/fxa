import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Scanner;

public class FxaClient {

    public static short port;
    public static InetAddress netEmuAddress;
    public static short netEmuPort;

    public static void main(String[] args) {

        if (args.length != 3)
            throw new IllegalArgumentException("Incorrect parameters");

        port = Short.parseShort(args[0]);
        //TODO: make sure port is valid, odd and equal to server port+1
        try{
            netEmuAddress = InetAddress.getByAddress(args[1].getBytes());
        } catch (UnknownHostException e){
            e.printStackTrace();
            System.out.println("Unknown host.");
        }
        netEmuPort = Short.parseShort(args[2]);
        //TODO: make sure port is valid

        Scanner sc = new Scanner(System.in);

        while(true){
            String command = sc.nextLine();
            String[] parts = command.split(" ");
            if(command.equals("connect")){
                //TODO
            } else if (parts[0].equals("get")){
                //TODO: download file (parts[1]) from the server (if F exists in the same directory with the FxA-server program)
            } else if (parts[0].equals("post")){
                //TODO
            } else if (parts[0].equals("window")){
                //TODO
            } else if (command.equals("disconnect")){
                //TODO
                break;
            } else {
                System.out.println("Not a valid command.");
            }
        }
    }
}
