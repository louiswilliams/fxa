import java.io.*;

/**
 * Created by Julia on 11/22/15.
 */
public class FxaFileTransfer {

    RxpSocket socket;

    private static final String POST_HEADER = "POST";
    private static final String GET_HEADER = "GET";

    private static final String LENGTH_HEADER = "LENGTH";

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


    /**
     *
     *
     *
     * @param fileName
     * @throws IOException
     */
    public void getFile(String fileName) throws IOException {
        OutputStream outputStream = socket.getOutputStream();
        BufferedReader inputStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        String header = GET_HEADER + " " + fileName + "\n";
        outputStream.write(header.getBytes());

        File file = new File(fileName);
        FileOutputStream fileOutput = new FileOutputStream(file);

        int fileLength = -1;
        String line = inputStream.readLine();
        String[] splitLine = line.split(" ");

        if (splitLine.length == 3) {
            if (splitLine[0].equalsIgnoreCase(POST_HEADER)) {
                if (!splitLine[2].equalsIgnoreCase(fileName)) {
                    throw new IOException("Receiving wrong file");
                }
                fileLength = Integer.parseInt(splitLine[1]);
            }
        } else {
            throw new IOException("Received bad header format.");
        }
        byte[] buffer = new byte[1024];
        int bytesRead = 0;
        int totalBytesRead = 0;
        while((bytesRead = socket.getInputStream().read(buffer)) != -1 && totalBytesRead<fileLength){
            totalBytesRead += bytesRead;
            fileOutput.write(buffer, 0, bytesRead);
        }
    }
}
