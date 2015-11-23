import javax.sound.midi.SysexMessage;
import java.io.*;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

public class FxaClient {

    public static short port;
    public static InetAddress netEmuAddress;
    public static short netEmuPort;
    private static FxaFileTransfer fileTransfer;

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

        Scanner keyboard = new Scanner(System.in);

        DatagramSocket netSock = new DatagramSocket(port);
        netSock.connect(netEmuAddress, netEmuPort);
        RxpSocket socket = new RxpSocket(netSock);

        fileTransfer = new FxaFileTransfer(socket);

//        PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream));
//        InputStream inputStream = socket.getInputStream();

        System.out.println("Begin message: ");
//        String line;
//        while (true) {
//            line = keyboard.nextLine();
//            outputStream.write(line.getBytes());
//        }

        new Thread(new Runnable()
        {

            @Override
            public void run() {
                while(true) {

                }
            }
        }).start();

        while(true){
            String command = keyboard.nextLine();

            if (command.equals("")){
                System.out.println("Please enter a command.");
            } else {
                String[] parts = command.split(" ");
                if(command.equals("connect")){
                    socket.connect(netEmuAddress, (short) (port + 1));
                }
                else if (parts[0].equals("get") && parts.length == 2){
                    fileTransfer.getFile(parts[1]);
                    //TODO: make sure this works
                }
                else if (parts[0].equals("post") && parts.length == 2){
                    try{
                        fileTransfer.postFile(new File(parts[1]));
                    } catch (NoSuchFileException e){
                        System.out.println("File not found.");
                    }
                    //TODO: make sure this works
                }
                else if (parts[0].equals("window")&& parts.length == 2){
                    try{
                        short size = Short.parseShort(parts[1]);
                        if(size>0){
                            socket.setWindowSize(size);
                        } else
                            System.out.println("Window size must be a positive integer.");
                    }catch (NumberFormatException e){
                        System.out.println("Not a valid window size.");
                    }
                    //TODO: make sure this works
                }
                else if (command.equals("disconnect")){
                    socket.close();
                    break;
                }
                else {
                    System.out.println("Not a valid command. Please try again.");
                }

            }
        }

        System.exit(0);
    }
}
