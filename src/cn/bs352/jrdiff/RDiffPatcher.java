package cn.bs352.jrdiff;

import java.io.*;

/**
 * @author bo.shen
 */
public class RDiffPatcher {

    public static final int DELTA_MAGIC = 0x72730236;

    public void apply(RandomAccessFile oldFile, InputStream delta, OutputStream newFile) throws IOException {
        DataInputStream dIn = new DataInputStream(delta);

        if (dIn.readInt() != DELTA_MAGIC) {
            throw new IOException("Invalid delta header.");
        }

        try {
            do {
                processBlock(dIn, oldFile, newFile);
            }
            while (true); // Until EOF Exception
        }
        catch (EOFException e) {
            // Check file length?
        }
    }

    private void processBlock(DataInputStream delta, RandomAccessFile oldFile, OutputStream newFile) throws IOException {
        byte type = delta.readByte();
        switch (type) {
            // Data block
            case 0x41:
                processDataBlock(delta.readUnsignedByte(), delta, newFile);
                break;
            case 0x42:
                processDataBlock(delta.readUnsignedShort(), delta, newFile);
                break;
            case 0x44:
                processDataBlock(delta.readInt(), delta, newFile);
                break;
            case 0x51:
                processOffsetBlock(oldFile, newFile, delta.readInt(), delta.readUnsignedByte());
                break;
            case 0x52:
                processOffsetBlock(oldFile, newFile, delta.readInt(), delta.readUnsignedShort());
                break;
            case 0x54:
                processOffsetBlock(oldFile, newFile, delta.readInt(), delta.readInt());
                break;
            case 0x61:
                processOffsetBlock(oldFile, newFile, delta.readLong(), delta.readUnsignedByte());
                break;
            case 0x62:
                processOffsetBlock(oldFile, newFile, delta.readLong(), delta.readUnsignedShort());
                break;
            case 0x64:
                processOffsetBlock(oldFile, newFile, delta.readLong(), delta.readInt());
                break;
            default:
                throw new IOException("Unknown block format");
        }
    }

    private void processOffsetBlock(RandomAccessFile oldFile, OutputStream newFile, long offset, int blockLength) throws IOException {
        oldFile.seek(offset);
        byte[] data = new byte[blockLength];
        int read = 0;
        while (read < blockLength) {
            read += oldFile.read(data, read, blockLength - read);
        }
        newFile.write(data);
    }

    private void processDataBlock(int blockLength, DataInputStream delta, OutputStream newFile) throws IOException {
        byte[] data = new byte[blockLength];
        int read = 0;
        while (read < blockLength) {
            read += delta.read(data, read, blockLength - read);
        }

        newFile.write(data);
    }
}
