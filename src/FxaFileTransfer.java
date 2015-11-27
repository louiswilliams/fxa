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

    /**
     * Post a file to a remote host
     *
     * @param file File to send
     * @throws IOException Thrown if file does not exist or from connection problems
     */
    public void postFile(File file) throws IOException {
        OutputStream outputStream = socket.getOutputStream();
        InputStream inputStream = socket.getInputStream();

        String header;
        if (file.exists()) {
            header = getRequestHeader(POST_HEADER, String.valueOf(file.length()), file.getName());
        } else {
            throw new FxaProtocolException("File not found: " + file.getName());
        }

        outputStream.write(header.getBytes());
        sendFile(file);

        String result = readLine(inputStream);
        String[] split = result.split(" ");
        String[] headersplit = result.split(":");
        if (headersplit.length >= 2) {

            if (split.length >= 2) {
                if (headersplit[0].equalsIgnoreCase(STATUS_ERR)) {
                    throw new FxaProtocolException(result.substring(STATUS_ERR.length()));
                } else if (!headersplit[0].equalsIgnoreCase(STATUS_OK)) {
                    throw new FxaProtocolException("Invalid status received: " + result);
                }
                if (split.length >= 4) {
                    if (!split[3].equals(file.getName())) {
                        throw new FxaProtocolException("Server confirmed incorrect filename: " + split[3]);
                    }
                }
            }
        }
    }

    /**
     * Request file from remote host
     *
     * @param fileName File request
     * @throws IOException Thrown if there are connection problems
     */
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

    /**
     * Respond to a request to get a file
     *
     * @param file File requested
     * @throws IOException Thrown from connection problems
     */
    public void respondToGetFile(File file) throws IOException {
        socket.setTransferring(true);

        OutputStream outputStream = socket.getOutputStream();
        String response;
        if (file.exists()) {
            response = getResponseHeader(STATUS_OK, GET_HEADER, String.valueOf(file.length()), file.getName());
            System.out.println("[FXA] Resp: " + response);
            outputStream.write(response.getBytes());
            sendFile(file);
        } else {
            response = getResponseHeader(STATUS_ERR, GET_HEADER, "File '" + file.getName() + "' does not exists");
            System.out.println("[FXA] Resp: " + response);
            socket.setTransferring(true);
            outputStream.write(response.getBytes());
        }
    }

    /**
     * Respond to a request to post a file
     *
     * @param file File being sent
     * @param len Size of file, in bytes
     * @throws IOException Thrown if there are connection issues
     */
    public void respondToPostFile(String file, int len) throws IOException {
        socket.setTransferring(true);

        OutputStream outputStream = socket.getOutputStream();
        String response;
        try {
            File received = receiveFile(file, len);
            response = getResponseHeader(STATUS_OK, POST_HEADER, String.valueOf(received.length()), received.getName());
        } catch (IOException e) {
            response = getResponseHeader(STATUS_ERR, POST_HEADER, "File could not be received: " + e.getMessage());
        }
        System.out.println("[FXA] Resp: " + response);
        outputStream.write(response.getBytes());
    }

    /**
     * Send a file to a remote host
     *
     * @param filename File to send
     * @throws IOException Thrown if there are connection issues
     */
    public void sendFile(File filename) throws IOException {
        socket.setTransferring(true);

        OutputStream outputStream = socket.getOutputStream();
        FileInputStream inputStream = new FileInputStream(filename);
        long length = filename.length();

        System.out.println("[FXA] Sending " + filename.getName() + " of " + length + " bytes");

        byte buffer[] = new byte[RxpSocket.MSS];
        int read;
        int totalSent = 0;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
            totalSent += read;
            if (socket.debugEnabled) {
                System.out.print("[FXA] Sent " + 100 * (totalSent / (float) length) + "%\n");
            } else {
                System.out.print("[FXA] Sent " + 100 * (totalSent / (float) length) + "%\r");
            }
        }
        System.out.println("\n[FXA] File send complete");
        socket.setTransferring(false);
    }

    /**
     * Receive file from remote host
     *
     * @param fileName Name of file being transferred
     * @param length Size of file, in bytes
     * @return File successfully received
     * @throws IOException Throw if there are connection issues
     */
    public File receiveFile(String fileName, int length) throws IOException {
        socket.setTransferring(true);

        makeDownloadDir();
        File file = new File(DOWLOAD_DIR + "/" + fileName);

        System.out.println("[FXA] Receiving " + fileName + " of " + length + " bytes");

        FileOutputStream fileOutput = new FileOutputStream(file);

        byte[] buffer = new byte[RxpSocket.MSS];
        int bytesRead = 0;
        int totalBytesRead = 0;
        int toRead;
        while (totalBytesRead < length) {
            toRead = Math.min(buffer.length, length - totalBytesRead);
            bytesRead = socket.getInputStream().read(buffer, 0, toRead);

            if (bytesRead != -1) {
                totalBytesRead += bytesRead;
                fileOutput.write(buffer, 0, bytesRead);
            } else {
                socket.setTransferring(false);
                break;
            }
            if (socket.debugEnabled) {
                System.out.print("[FXA] Received " + 100 * (totalBytesRead / (float) length) + "%\n");
            } else {
                System.out.print("[FXA] Received " + 100 * (totalBytesRead / (float) length) + "%\r");
            }
        }
        fileOutput.close();
        System.out.println("\n[FXA] File receive completed (" + totalBytesRead + " bytes)");
        socket.setTransferring(false);
        return file;
    }

    /**
     * Begin handling requests for a socket
     */
    public void serve() {
        new Thread(() -> {
            InputStream inputStream = socket.getInputStream();

            while(true){
                try {
                    String line = readLine(inputStream);

                    String[] request = line.split(" ");
                    System.out.println("[FXA] Req:  " + line);
                    if (request.length == 2 && request[0].trim().equalsIgnoreCase(GET_HEADER)) {
                        respondToGetFile(new File("src/" + request[1].trim()));
                    } else if (request.length == 3 && request[0].trim().equalsIgnoreCase(POST_HEADER)) {
                        respondToPostFile(request[2].trim(), Integer.parseInt(request[1]));
                    } else {
                        throw new IOException("Bad request");
                    }
                } catch (IOException e) {
                    System.err.println(e.getMessage());
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

    /**
     * Read from an block on an input stream until a newline character is sent
     *
     * @param stream InputStream to read from
     * @return Line read, not including the newline character
     * @throws IOException
     */
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
