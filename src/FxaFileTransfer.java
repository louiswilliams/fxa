import java.io.*;

public class FxaFileTransfer {

    RxpSocket socket;

    private static final String POST_HEADER = "POST";
    private static final String GET_HEADER = "GET";

    public FxaFileTransfer(RxpSocket socket) {
        this.socket = socket;
    }

    public void postFile(File file) throws IOException {
        FileInputStream inputStream = new FileInputStream(file);
        OutputStream outputStream = socket.getOutputStream();

        String header = POST_HEADER + " " + file.length() + " " + file.getName() + "\n";

        outputStream.write(header.getBytes());

        byte buffer[] = new byte[1024];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            socket.getOutputStream().write(buffer, 0, read);
        }
    }

    public void getFile(String fileName) throws IOException {
        OutputStream outputStream = socket.getOutputStream();
        BufferedReader inputStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        String header = GET_HEADER + " " + fileName + "\n";
        outputStream.write(header.getBytes());

        String line = inputStream.readLine();
        String[] splitLine = line.split(" ");

        if (splitLine.length == 3) {
            if (splitLine[0].equalsIgnoreCase(POST_HEADER)) {
                if (!splitLine[2].equalsIgnoreCase(fileName)) {
                    throw new IOException("Receiving wrong file");
                }
                int fileLength = Integer.parseInt(splitLine[1]);
                receiveFile(fileName, fileLength);
            }
        } else {
            throw new IOException("Received bad header format.");
        }
    }

    public void receiveFile(String fileName, int length) throws IOException {
        File file = new File("src/" + fileName);

        FileOutputStream fileOutput = new FileOutputStream(file);

        byte[] buffer = new byte[1024];
        int bytesRead;
        int totalBytesRead = 0;
        while((bytesRead = socket.getInputStream().read(buffer)) != -1 && totalBytesRead < length){
            totalBytesRead += bytesRead;
            fileOutput.write(buffer, 0, bytesRead);
        }
    }

    public void serve() {
        new Thread(() -> {
            InputStream inputStream = socket.getInputStream();
            while(true){
                try {
                    synchronized (((RxpInputStream)inputStream).getBuffer()) {
                        ((RxpInputStream)inputStream).getBuffer().wait();
                    }

                    String[] request = new String(((RxpInputStream)inputStream).getBuffer()).split(" ");
                    if (request.length == 2 && request[0].trim().equalsIgnoreCase(GET_HEADER)) {
                        postFile(new File("src/" + request[1].trim()));
                    } else if (request.length == 3 && request[0].trim().equalsIgnoreCase(POST_HEADER)) {
                        receiveFile(request[2].trim(), Integer.parseInt(request[1]));
                    } else {
                        throw new IOException("Bad request");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e){
                    e.printStackTrace();
                }
            }

        }).start();
    }
}
