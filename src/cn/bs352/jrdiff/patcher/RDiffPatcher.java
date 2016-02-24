package cn.bs352.jrdiff.patcher;

import java.io.*;

/**
 * Patching Tool for transforming base file into newer version using delta file
 *
 * @author bo.shen
 */
public class RDiffPatcher {

    // Use a standalone field
    private static final int DELTA_MAGIC = 0x72730236;

    private byte[] buffer = new byte[1024 * 1024]; // 1MB Buffer

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
                processOffsetBlock(oldFile, newFile, toUnsignedInt(delta.readInt()), delta.readUnsignedByte());
                break;
            case 0x52:
                processOffsetBlock(oldFile, newFile, toUnsignedInt(delta.readInt()), delta.readUnsignedShort());
                break;
            case 0x54:
                processOffsetBlock(oldFile, newFile, toUnsignedInt(delta.readInt()), delta.readInt());
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

    private static long toUnsignedInt(int i) {
        return i & 0xFFFFFFFFL;
    }

    private void processOffsetBlock(RandomAccessFile oldFile, OutputStream newFile, long offset, int blockLength) throws IOException {
        // offset & block length could be null
        oldFile.seek(offset);
        int toRead = blockLength;
        while (toRead > 0) {
            int read = oldFile.read(buffer, 0, Math.min(toRead, buffer.length));
            if (read > 0) {
                newFile.write(buffer, 0, read);
                toRead -= read;
            }
        }
    }

    private void processDataBlock(int blockLength, DataInputStream delta, OutputStream newFile) throws IOException {
        int toRead = blockLength;
        while (toRead > 0) {
            int read = delta.read(buffer, 0, Math.min(toRead, buffer.length));
            if (read > 0) {
                newFile.write(buffer, 0, read);
                toRead -= read;
            }
        }
    }
}
