import jdk.internal.util.xml.impl.Input;

import java.io.*;

public class FxaFileTransfer {

    RxpSocket socket;

    private static final String POST_HEADER = "POST";
    private static final String GET_HEADER = "GET";

    private static final String STATUS_OK = "OKAY";
    private static final String STATUS_ERR = "ERROR";

    public static final String DOWLOAD_DIR = "download";

    public FxaFileTransfer(RxpSocket socket) {
        this.socket = socket;
    }

    public void postFile(File file) throws IOException {
        OutputStream outputStream = socket.getOutputStream();
        InputStream inputStream = socket.getInputStream();
        String header = getRequestHeader(POST_HEADER, String.valueOf(file.length()), file.getName());
        outputStream.write(header.getBytes());
        sendFile(file);

        String result = readLine(inputStream);
        String[] split = result.split(" ");
        if (split.length >= 2) {
            if (split[0].equalsIgnoreCase(STATUS_ERR)) {
                throw new FxaProtocolException(result.substring(STATUS_ERR.length()));
            } else if (!split[0].equalsIgnoreCase(STATUS_OK)) {
                throw new FxaProtocolException("Invalid status received: " + result);
            }
        }
    }

    public void getFile(String fileName) throws IOException {
        OutputStream outputStream = socket.getOutputStream();
        InputStream inputStream = socket.getInputStream();

        String header = getRequestHeader(GET_HEADER, fileName);
        outputStream.write(header.getBytes());

        String line = readLine(inputStream);
        String[] headerSplit = line.split(":");
        String[] splitLine = line.split(" ");

        if (headerSplit.length >= 2) {
            String status = headerSplit[0];

            if (status.equalsIgnoreCase(STATUS_OK)) {
                if (splitLine.length == 4) {
                    String method = splitLine[1];
                    int fileLength = Integer.parseInt(splitLine[2]);
                    String file = splitLine[3];
                    if (!method.equalsIgnoreCase(GET_HEADER)) {
                        throw new FxaProtocolException("Invalid method header received");
                    }
                    if (!file.equalsIgnoreCase(fileName)) {
                        throw new FxaProtocolException("Wrong file received");
                    }
                    receiveFile(fileName, fileLength);
                } else {
                    throw new FxaProtocolException("Received incorrect header format.");
                }
            } else if (status.equalsIgnoreCase(STATUS_ERR)) {
                throw new FxaProtocolException("Received error:" + line.substring(STATUS_ERR.length()));
            } else{
                throw new FxaProtocolException("Received incorrect status: " + status);
            }
        } else {
            throw new FxaProtocolException("Received incorrect header format.");
        }
    }

    public void respondToGetFile(File file) throws IOException {
        OutputStream outputStream = socket.getOutputStream();
        if (file.exists()) {
            String response = getResponseHeader(STATUS_OK, GET_HEADER, String.valueOf(file.length()), file.getName());
            outputStream.write(response.getBytes());
            sendFile(file);
        } else {
            String response = getResponseHeader(STATUS_ERR, GET_HEADER, "File '" + file.getName() + "' does not exists");
            outputStream.write(response.getBytes());
        }
    }

    public void respondToPostFile(String file, int len) throws IOException {
        OutputStream outputStream = socket.getOutputStream();
        String response;
        try {
            File received = receiveFile(file, len);
            response = getResponseHeader(STATUS_OK, POST_HEADER, String.valueOf(received.length()), received.getName());
        } catch (IOException e) {
            response = getResponseHeader(STATUS_ERR, POST_HEADER, "File could not be received: " + e.getMessage());
        }
        outputStream.write(response.getBytes());
    }

    public void sendFile(File filename) throws IOException {
        OutputStream outputStream = socket.getOutputStream();
        FileInputStream inputStream = new FileInputStream(filename);

        byte buffer[] = new byte[RxpSocket.MSS];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
    }

    public File receiveFile(String fileName, int length) throws IOException {
        makeDownloadDir();
        File file = new File(DOWLOAD_DIR + "/" + fileName);

        FileOutputStream fileOutput = new FileOutputStream(file);

        byte[] buffer = new byte[1024];
        int bytesRead = 0;
        int totalBytesRead = 0;
        int toRead;
        while (bytesRead != -1 && totalBytesRead < length) {
            toRead = Math.min(buffer.length, length - totalBytesRead);
            bytesRead = socket.getInputStream().read(buffer, 0, toRead);

            if (bytesRead != -1) {
                totalBytesRead += bytesRead;
                fileOutput.write(buffer, 0, bytesRead);
            }
        }
        System.out.println("Read " + totalBytesRead + " bytes");
        return file;
    }

    public void serve() {
        new Thread(() -> {
            InputStream inputStream = socket.getInputStream();

            while(true){
                try {
                    String line = readLine(inputStream);

                    String[] request = line.split(" ");
                    if (request.length == 2 && request[0].trim().equalsIgnoreCase(GET_HEADER)) {
                        respondToGetFile(new File("src/" + request[1].trim()));
                    } else if (request.length == 3 && request[0].trim().equalsIgnoreCase(POST_HEADER)) {
                        respondToPostFile(request[2].trim(), Integer.parseInt(request[1]));
                    } else {
                        throw new IOException("Bad request");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }).start();
    }

    public static void makeDownloadDir() {
        (new File(DOWLOAD_DIR)).mkdir();
    }

    public static String getResponseHeader(String status, String method, String ... args) {
        return status + ": " + getRequestHeader(method, args);
    }

    public static String getRequestHeader(String method, String ... args) {
        return method + " " + String.join(" ", args) + "\n";
    }

    public static String readLine(InputStream stream) throws IOException {
        byte buffer[] = new byte[RxpSocket.UDP_MAX];
        int i = 0;
        byte b;
        while ((b = (byte) stream.read()) != '\n') {
            buffer[i++] = b;
        }
        return new String(buffer, 0, i);
    }
}
