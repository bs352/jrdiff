package cn.bs352.jrdiff.common;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Rolling Checksum based on Adler-32 checksum
 * but with modulo 2^16 for speed (no need to mod if using a short data type)
 *
 * @author bo.shen
 */
public class RollingChecksum {

    private static final int CHAR_OFFSET = 31;

    private final int blockSize;

    private short s1;
    private short s2;

    private byte[] rollingBuffer;
    private int rollingPtr = 0;

    public RollingChecksum(int blockSize) {
        this.blockSize = blockSize;
        reset();
    }

    public void update(byte[] bytes) {
        update(bytes, 0, bytes.length);
    }

    public void update(byte[] bytes, int offset, int len) {
        for (int i = offset; i < offset + len; i++) {
            int newByte = (256 + bytes[i]) % 256;
            s1 += newByte + CHAR_OFFSET;
            s2 += s1;
        }

        if (len == blockSize) {
            rollingBuffer = Arrays.copyOfRange(bytes, offset, offset + len);
            rollingPtr = 0;
        }
    }

    /**
     * Rolls in a byte,
     * need to remove the byte at front
     *
     * @param b
     */
    public void roll(byte b) {
        // Rolls in a byte, replace the byte at "rollingPtr"
        int newByte = (256 + b) % 256;
        int old = (256 + rollingBuffer[rollingPtr]) % 256;
        s1 -= old + CHAR_OFFSET;
        s2 -= (old + CHAR_OFFSET) * blockSize;

        s1 += newByte + CHAR_OFFSET;
        s2 += s1;

        rollingBuffer[rollingPtr] = b;
        rollingPtr++;
        if (rollingPtr == rollingBuffer.length) {
            rollingPtr = 0;
        }
    }

    public int digest() {
        return (s1 & 0xFFFF) + (s2 << 16);
    }

    public void reset() {
        s1 = 0;
        s2 = 0;
        rollingBuffer = null;
        rollingPtr = 0;
    }

    public static int checksum(InputStream stream) throws IOException {
        RollingChecksum checksum = new RollingChecksum(2048);
        int read;
        byte[] byteBuffer = new byte[1024 * 1024];
        while (-1 != (read = stream.read(byteBuffer))) {
            checksum.update(byteBuffer, 0, read);
        }
        return checksum.digest();
    }
}
