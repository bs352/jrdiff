package cn.bs352.jrdiff.generator;

import cn.bs352.jrdiff.common.RollingChecksum;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @author bo.shen
 */
class RDiffSignatureGenerator {

    private static final int BUFFER_SIZE = 32768;

    private final MessageDigest strongChecksum;
    private final RollingChecksum rollingChecksum;
    private final int blockLength;
    private final int strongHashLength;

    private int bytesHashed = 0;

    RDiffSignatureGenerator(int blockLength, int strongHashLength) throws NoSuchAlgorithmException {
        this.blockLength = blockLength;
        this.strongHashLength = strongHashLength;
        strongChecksum = MessageDigest.getInstance("MD5");
        rollingChecksum = new RollingChecksum(blockLength);
    }

    void generate(InputStream in, OutputStream out) throws IOException {
        BufferedInputStream stream = new BufferedInputStream(in);
        DataOutputStream outStream = new DataOutputStream(out);

        outStream.writeInt(RDiffGenerator.SIG_MAGIC);
        outStream.writeInt(blockLength);
        outStream.writeInt(strongHashLength);

        int len;
        byte[] buf = new byte[BUFFER_SIZE];
        while ((len = stream.read(buf)) != -1) {
            update(buf, 0, len, outStream);
        }

        // Last bit may be less than "block length" long
        if (bytesHashed > 0) {
            writeSigBlock(outStream);
        }
    }

    void update(byte[] buf, int offset, int len, DataOutputStream out) throws IOException {
        int bytesToProcess = len;
        int bytesOffset = offset;

        while (bytesToProcess > 0) {
            int bytesToHash = blockLength - bytesHashed;

            if (bytesToProcess < bytesToHash) {
                // Not enough, hash all
                rollingChecksum.update(buf, bytesOffset, bytesToProcess);
                strongChecksum.update(buf, bytesOffset, bytesToProcess);
                bytesHashed += bytesToProcess;
                bytesToProcess = 0;
            }
            else {
                rollingChecksum.update(buf, bytesOffset, bytesToHash);
                strongChecksum.update(buf, bytesOffset, bytesToHash);

                writeSigBlock(out);

                rollingChecksum.reset();
                strongChecksum.reset();

                bytesToProcess -= bytesToHash;
                bytesOffset += bytesToHash;
                bytesHashed = 0;
            }
        }

    }

    private void writeSigBlock(DataOutputStream out) throws IOException {
        out.writeInt(rollingChecksum.digest());
        out.write(strongChecksum.digest(), 0, strongHashLength);
    }
}
