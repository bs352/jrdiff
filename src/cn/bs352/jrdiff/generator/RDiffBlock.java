package cn.bs352.jrdiff.generator;

/**
 * A delta block describes a data block in new file, a block contains either
 *
 * New data that cannot be found in original file. Max data block size is 10MB
 *  - OR -
 * Offsets and length into original file
 * Offset blocks do not contain real data, block length is limited by 32bit signed int type: 2GB
 *
 * @author bo.shen
 */
class RDiffBlock {

    private static final int MAX_DATA_BLOCK_LENGTH = 1024 * 1024 * 10; // 10MB

    private boolean isData;

    private int blockLength;

    private byte[] data;

    private long oldOffset;

    RDiffBlock(byte[] data) {
        this.data = data;
        this.blockLength = data.length;
        isData = true;
    }

    RDiffBlock(long oldOffset, int blockLength) {
        this.oldOffset = oldOffset;
        this.blockLength = blockLength;
        isData = false;
    }

    boolean isData() {
        return isData;
    }

    byte[] getData() {
        return data;
    }

    long getOldOffset() {
        return oldOffset;
    }

    int getBlockLength() {
        return blockLength;
    }

    /**
     * Try
     * @param newBlock
     * @return
     */
    boolean tryMerge(RDiffBlock newBlock) {
        if (isData != newBlock.isData) {
            return false;
        }
        if (isData) {
            // 2 adjacent data blocks
            int newBlockLength = this.blockLength + newBlock.blockLength;
            if (newBlockLength > MAX_DATA_BLOCK_LENGTH) {
                return false;
            }
            else {
                this.blockLength = newBlockLength;
                byte[] bytes = new byte[blockLength];
                int length = this.data.length;
                System.arraycopy(this.data, 0, bytes, 0, length);
                System.arraycopy(newBlock.data, 0, bytes, length, newBlock.data.length);

                this.data = bytes;
                return true;
            }
        }
        else {
            // 2 adjacent reference blocks
            if (blockLength + newBlock.blockLength < 0) {
                // Length Overflows 2GB, unusual but possible. Split into multiple blocks
                return false;
            }
            else if (oldOffset + blockLength == newBlock.oldOffset) {
                this.blockLength += newBlock.blockLength;
                return true;
            }
            else {
                return false;
            }
        }
    }
}
