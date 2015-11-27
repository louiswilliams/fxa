import java.io.*;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Scanner;

public class FxaClient {

    public static short port;
    public static InetAddress netEmuAddress;
    public static short netEmuPort;
    private static FxaFileTransfer fileTransfer;

    public static void main(String[] args) throws IOException {

        if (args.length != 3)
            throw new IllegalArgumentException("Incorrect number of parameters.");

        port = Short.parseShort(args[0]);
        if(!isValidPort(port) ||  port%2 == 1)
            throw new IllegalArgumentException("First port number is invalid.");

        try{
            netEmuAddress = InetAddress.getByName(args[1]);
        } catch (UnknownHostException e){
            e.printStackTrace();
            System.out.println("Unknown host.");
        }

        if(!isValidPort(Short.parseShort(args[2])))
            throw new IllegalArgumentException("Second port number is invalid.");
        else
            netEmuPort = Short.parseShort(args[2]);

        Scanner keyboard = new Scanner(System.in);

        DatagramSocket netSock = new DatagramSocket(port);
        netSock.connect(netEmuAddress, netEmuPort);
        RxpSocket socket = new RxpSocket(netSock);

        fileTransfer = new FxaFileTransfer(socket);

        while(true){
            String command = keyboard.nextLine();

            if (command.equals("")){
                System.out.println("Please enter a command.");
            } else {
                String[] parts = command.split(" ");
                if(command.equalsIgnoreCase("connect")){
                    try {
                        socket.connect(netEmuAddress, (short) (port + 1));
                        socket.resetInputStream();
                        socket.resetOutputStream();
                        fileTransfer = new FxaFileTransfer(socket);
                        System.out.println("Connected");
                    } catch (IOException e) {
                        System.err.println(e.getMessage());
                    }
                }
                else if (parts[0].equalsIgnoreCase("get") && parts.length == 2){
                    System.out.println("In get and socket state: " + socket.getState());
                    new Thread(() -> {
                        try {
                            fileTransfer.getFile(parts[1]);
                        }catch (IOException e) {
                            System.err.println("Error: " + e.getMessage());
                        }
                    }).start();
                }
                else if (parts[0].equalsIgnoreCase("post") && parts.length == 2){
                    try{
                        fileTransfer.postFile(new File("src/" + parts[1])); //TODO: new thread? cant send and type disconnect command at same time
                    } catch (IOException e){
                        System.err.println("Error: " + e.getMessage());
                    }
                }
                else if (parts[0].equalsIgnoreCase("window")&& parts.length == 2){
                    try{
                        short size = Short.parseShort(parts[1]);
                        if(size>0){
                            socket.setRecvWindowSize(size);
                            System.out.println("Client window size set to " + socket.getRecvWindowSize());
                        } else
                            System.out.println("Window size must be a positive integer.");
                    }catch (NumberFormatException e){
                        System.out.println("Not a valid window size.");
                    }
                }
                else if (command.equalsIgnoreCase("disconnect")){
                    socket.sendFin();
                }
                else {
                    System.out.println("Not a valid command. Please try again.");
                }

            }
        }
    }

    private static boolean isValidPort(short port){
        return(port>1 && port<65535);
    }
}
