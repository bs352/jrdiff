package cn.bs352.jrdiff.generator;

import cn.bs352.jrdiff.common.RollingChecksum;

import java.io.*;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * @author bo.shen
 */
class RDiffDeltaGenerator {

    private static final int BUFFER_SIZE = 32768;

    private int blockLength;
    private int strongHashLength;

    private RollingChecksum rollingChecksum;
    private SignatureLookupTable lookupTable;
    private byte[] dataBuffer = new byte[BUFFER_SIZE];
    private int nextBufIdx = 0;

    private RDiffBlock deltaBlockToWrite = null;

    void generate(InputStream sigFile, InputStream newFile, OutputStream delta) throws IOException, NoSuchAlgorithmException, DigestException {
        // Read Signatures and setup look up table
        lookupTable = new SignatureLookupTable(readSignatures(sigFile), strongHashLength);
        rollingChecksum = new RollingChecksum(blockLength);

        DataOutputStream out = new DataOutputStream(delta);
        out.writeInt(RDiffGenerator.DELTA_MAGIC);

        createDelta(newFile, out);
    }

    void createDelta(InputStream stream, DataOutputStream out) throws IOException, DigestException {
        int len;
        byte[] buf = new byte[BUFFER_SIZE];

        while ((len = stream.read(buf)) != -1) {
            update(buf, 0, len, out);
        }

        // Last bit may be less than "block length" long
        if (nextBufIdx > 0) {
            rollingChecksum.reset();
            rollingChecksum.update(dataBuffer, 0, nextBufIdx);
            int weak = rollingChecksum.digest();
            SigBlock block = lookupTable.check(weak, dataBuffer, nextBufIdx, nextBufIdx, strongHashLength);
            if (block == null) {
                writeBlock(out, new RDiffBlock(Arrays.copyOfRange(dataBuffer, 0, nextBufIdx)));
            }
            else {
                writeBlock(out, new RDiffBlock(block.getOffset(), nextBufIdx));
            }
        }

        if (deltaBlockToWrite != null) {
            writeBlockNow(out, deltaBlockToWrite);
        }
    }

    void update(byte[] buf, int offset, int len, DataOutputStream out) throws DigestException, IOException {
        for (int i = offset; i < offset + len; i++) {
            byte b = buf[i];
            dataBuffer[nextBufIdx++] = b;
            if (nextBufIdx < blockLength) {
                continue;
            }
            else if (nextBufIdx == blockLength) {
                // just reached a full block
                rollingChecksum.reset();
                rollingChecksum.update(dataBuffer, 0, blockLength);
            }
            else {
                rollingChecksum.roll(b);
            }

            int checksum = rollingChecksum.digest();

            // Do Lookup
            SigBlock hitBlock = lookupTable.check(checksum, dataBuffer, nextBufIdx, blockLength, strongHashLength);
            if (hitBlock != null) {
                // Hit
                int diffBytes = nextBufIdx - blockLength;
                if (diffBytes > 0) {
                    writeBlock(out, new RDiffBlock(Arrays.copyOfRange(dataBuffer, 0, diffBytes)));
                }
                writeBlock(out, new RDiffBlock(hitBlock.getOffset(), blockLength));
                nextBufIdx = 0;
            }
            else {
                if (nextBufIdx == dataBuffer.length) {
                    // Buffer full, leave the last 2048 bytes intact, pack the bytes before that into a block
                    int packSize = nextBufIdx - blockLength;
                    writeBlock(out, new RDiffBlock(Arrays.copyOf(dataBuffer, packSize)));
                    nextBufIdx -= packSize;
                    // Move last 2048 bytes to front
                    System.arraycopy(dataBuffer, dataBuffer.length - blockLength, dataBuffer, 0, blockLength);
                }
            }
        }
    }

    private void writeBlock(DataOutputStream out, RDiffBlock deltaBlock) throws IOException {
        if (deltaBlockToWrite != null) {
            if (!deltaBlockToWrite.tryMerge(deltaBlock)) {
                writeBlockNow(out, deltaBlockToWrite);
                deltaBlockToWrite = deltaBlock;
            }
        }
        else {
            deltaBlockToWrite = deltaBlock;
        }
    }

    private void writeBlockNow(DataOutputStream out, RDiffBlock deltaBlock) throws IOException {
        if (deltaBlock.isData()) {
            int len = byteCount(deltaBlock.getBlockLength());
            out.writeByte(RDiffGenerator.FLAG_DATA[len]);
            writeBlockLength(out, deltaBlock, len);
            out.write(deltaBlock.getData());
        }
        else {
            int count = byteCount(deltaBlock.getOldOffset());
            int len = byteCount(deltaBlock.getBlockLength());
            if (count <= 4) {
                out.writeByte(RDiffGenerator.FLAG_REF_4[len]);
                out.writeInt((int) deltaBlock.getOldOffset());
                writeBlockLength(out, deltaBlock, len);
            }
            else {
                out.writeByte(RDiffGenerator.FLAG_REF_8[len]);
                out.writeLong(deltaBlock.getOldOffset());
                writeBlockLength(out, deltaBlock, len);
            }
        }
    }

    private void writeBlockLength(DataOutputStream out, RDiffBlock deltaBlock, int len) throws IOException {
        switch (len) {
            case 1:
                out.writeByte(deltaBlock.getBlockLength());
                break;
            case 2:
                out.writeShort(deltaBlock.getBlockLength());
                break;
            case 4:
                out.writeInt(deltaBlock.getBlockLength());
                break;
            default:
                throw new IllegalStateException("Invalid byte count: " + len);
        }
    }

    /**
     * Checks if a long integer can be represented by 1,2,4, or 8 bytes
     */
    private static int byteCount(long l) {
        if ((l & ~0xFFL) == 0) {
            return 1;
        }
        else if ((l & ~0xFFFFL) == 0) {
            return 2;
        }
        else if ((l & ~0xFFFFFFFFL) == 0) {
            return 4;
        }
        return 8;
    }

    private List<SigBlock> readSignatures(InputStream sigFile) throws IOException {
        List<SigBlock> sigBlocks = new LinkedList<SigBlock>();
        DataInputStream stream = new DataInputStream(sigFile);
        int magic = stream.readInt();

        if (magic != RDiffGenerator.SIG_MAGIC) {
            throw new IOException("Invalid signature header.");
        }

        blockLength = stream.readInt();
        strongHashLength = stream.readInt();
        long offset = 0;

        byte[] strongBuffer = new byte[strongHashLength];
        try {
            do {
                int weak = stream.readInt();
                int read = 0;
                do {
                    read += stream.read(strongBuffer, read, strongHashLength - read);
                }
                while (read < strongHashLength);

                SigBlock sigBlock = new SigBlock(weak, strongBuffer, offset);
                sigBlocks.add(sigBlock);
                offset += blockLength;
            }
            while (true);
        }
        catch (EOFException e) {
            // Do nothing
        }
        return sigBlocks;
    }

    private static class SigBlock {

        private int weakChecksum;
        private byte[] strongHash;
        private long offset;

        public SigBlock(int weakChecksum, byte[] strongHash, long offset) {
            this.weakChecksum = weakChecksum;
            this.strongHash = Arrays.copyOf(strongHash, strongHash.length);
            this.offset = offset;
        }

        public int getWeakChecksum() {
            return weakChecksum;
        }

        public byte[] getStrongHash() {
            return strongHash;
        }

        public long getOffset() {
            return offset;
        }
    }

    private final static class MD5Key {
        private byte[] md5;

        public MD5Key(byte[] md5, int strongHashLength) {
            this.md5 = Arrays.copyOf(md5, strongHashLength);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            MD5Key md5Key = (MD5Key) o;

            if (!Arrays.equals(md5, md5Key.md5)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(md5);
        }
    }

    private static class SignatureLookupTable {

        private Map<Integer, Map<Integer, Map<MD5Key, SigBlock>>> lookupTable;
        private MessageDigest strongChecksum;

        public SignatureLookupTable(List<SigBlock> blocks, int strongHashLength) throws NoSuchAlgorithmException {
            lookupTable = new HashMap<Integer, Map<Integer, Map<MD5Key, SigBlock>>>();
            strongChecksum = MessageDigest.getInstance("md5");
            for (SigBlock block : blocks) {
                int weak = block.getWeakChecksum();
                int firstLevel = weak & 0xFFFF;

                Map<Integer, Map<MD5Key, SigBlock>> secondLevelMap = lookupTable.get(firstLevel);
                if (secondLevelMap == null) {
                    secondLevelMap = new HashMap<Integer, Map<MD5Key, SigBlock>>();
                    lookupTable.put(firstLevel, secondLevelMap);
                }

                Map<MD5Key, SigBlock> thirdLevel = secondLevelMap.get(weak);
                if (thirdLevel == null) {
                    thirdLevel = new HashMap<MD5Key, SigBlock>();
                    secondLevelMap.put(weak, thirdLevel);
                }

                // Duplicate Strong Hash are simply replaced
                thirdLevel.put(new MD5Key(block.getStrongHash(), strongHashLength), block);
            }
        }

        public SigBlock check(int weak, byte[] dataBuffer, int nextBufIdx, int blockLength, int strongHashLength) throws DigestException {
            Map<MD5Key, SigBlock> secondLevel = checkWeak(weak);
            if (secondLevel != null) {
                // Weak hit, need to check strong hash
                strongChecksum.reset();
                strongChecksum.update(dataBuffer, Math.max(0, nextBufIdx - blockLength), blockLength);
                MD5Key key = new MD5Key(strongChecksum.digest(), strongHashLength);

                return secondLevel.get(key);
            }
            return null;
        }

        private Map<MD5Key, SigBlock> checkWeak(int weak) {
            int firstLevel = weak & 0xFFFF;
            Map<Integer, Map<MD5Key, SigBlock>> secondLevel = lookupTable.get(firstLevel);
            if (secondLevel != null) {
                return secondLevel.get(weak);
            }
            else {
                return null;
            }
        }
    }
}
