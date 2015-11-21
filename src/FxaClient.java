import javax.sound.midi.SysexMessage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Scanner;

public class FxaClient {

    public static short port;
    public static InetAddress netEmuAddress;
    public static short netEmuPort;

    public static void main(String[] args) throws IOException {

        if (args.length != 3)
            throw new IllegalArgumentException("Incorrect parameters");

        port = Short.parseShort(args[0]);
        //TODO: make sure port is valid, odd and equal to server port+1
        try{
            netEmuAddress = InetAddress.getByName(args[1]);
        } catch (UnknownHostException e){
            e.printStackTrace();
            System.out.println("Unknown host.");
        }
        netEmuPort = Short.parseShort(args[2]);
        //TODO: make sure port is valid

        Scanner sc = new Scanner(System.in);

        DatagramSocket netSock = new DatagramSocket(port);
        netSock.connect(netEmuAddress, netEmuPort);
        RxpSocket socket = new RxpSocket(netSock, netEmuAddress, (short) (port + 1));
        System.out.println("Created socket");
        socket.connect();

        OutputStream outputStream = socket.getOutputStream();
        InputStream inputStream = socket.getInputStream();

        String hello = "Hello message";
        outputStream.write(hello.getBytes());

        int b;
        try {
            while ((b = inputStream.read()) != -1) {
                System.out.print((byte) b);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

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
