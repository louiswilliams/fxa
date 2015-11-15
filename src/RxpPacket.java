import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class RxpPacket {

    public short destPort;
    public short srcPort;
    public short windowSize;
    public int sequence;
    public int acknowledgement;

    public boolean ack;
    public boolean nack;
    public boolean syn;
    public boolean fin;
    public boolean rst;
    public boolean auth;

    public byte[] data;

    public static final int HEADER_SIZE = 20;

    public byte[] getBytes() {

        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + data.length);

        buffer.putShort(0, destPort);
        buffer.putShort(2, srcPort);
        buffer.putInt(4, sequence);
        buffer.putInt(8, acknowledgement);

        byte flags = 0;
        flags |= boolValue(ack, 7);
        flags |= boolValue(nack, 6);
        flags |= boolValue(syn, 5);
        flags |= boolValue(fin, 4);
        flags |= boolValue(rst, 3);
        flags |= boolValue(auth, 2);
        buffer.put(12, flags);

        buffer.putShort(14, windowSize);

        for (int i = 0; i < data.length; i++) {
            buffer.put(HEADER_SIZE + i, data[i]);
        }

        byte hash[] = computeHash(buffer.array());

        for (int i = 0; i < hash.length; i++) {
            buffer.put(16 + i, hash[i]);
        }
        return buffer.array();
    }

    public RxpPacket (byte[] packet) throws InvalidChecksumException {
        ByteBuffer buffer = ByteBuffer.wrap(packet);

        destPort = buffer.getShort(0);
        srcPort = buffer.getShort(2);
        sequence = buffer.getInt(4);
        acknowledgement= buffer.getInt(8);

        byte flags = buffer.get(4);
        ack = getBool(flags, 7);
        nack = getBool(flags, 6);
        syn = getBool(flags, 5);
        fin = getBool(flags, 4);
        rst = getBool(flags, 3);
        auth = getBool(flags, 2);

        windowSize = buffer.getShort(14);
        data = new byte[packet.length - HEADER_SIZE];
        for (int i = 0; i < data.length; i++) {
            data[i] = buffer.get(i + HEADER_SIZE);
        }

        byte[] hash = computeHash(packet);
        for (int i = 0; i < hash.length; i++) {
            hash[i] = buffer.get(16 + i);
        }
        boolean equal = true;
        for (int i = 0; i < hash.length && equal; i++) {
            if (hash[i] != buffer.get(16 + i)) {
                equal = false;
            }
        }

        if (!equal) {
            throw new InvalidChecksumException();
        }
    }

    private static byte[] computeHash(byte[] preFinalized) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }

        md.update(preFinalized, 0, 16);
        md.update(preFinalized, HEADER_SIZE, preFinalized.length - HEADER_SIZE);

        ByteBuffer buffer = ByteBuffer.allocate(4);
        byte hash[] = md.digest();
        buffer.put(hash, 0 , 4);

        return buffer.array();
    }

    private static int boolValue(boolean b, int bit) {
        return (b ? 1 : 0) << bit;
    }

    private boolean getBool(byte i, int bit) {
        return (1 & (i >> bit)) == 1;
    }
}
