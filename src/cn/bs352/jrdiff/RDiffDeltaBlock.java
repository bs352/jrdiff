package cn.bs352.jrdiff;

/**
* @author bo.shen
*/
class RDiffDeltaBlock {

    private static final int MAX_DATA_BLOCK_LENGTH = 1024 * 1024 * 10; // 10MB

    private boolean isData;

    private int blockLength;

    private byte[] data;

    private long oldOffset;

    public RDiffDeltaBlock(byte[] data) {
        this.data = data;
        this.blockLength = data.length;
        isData = true;
    }

    public RDiffDeltaBlock(long oldOffset, int blockLength) {
        this.oldOffset = oldOffset;
        this.blockLength = blockLength;
        isData = false;
    }

    public boolean isData() {
        return isData;
    }

    public byte[] getData() {
        return data;
    }

    public long getOldOffset() {
        return oldOffset;
    }

    public int getBlockLength() {
        return blockLength;
    }

    public boolean tryMerge(RDiffDeltaBlock newBlock) {
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
            if (oldOffset + blockLength == newBlock.oldOffset) {
                this.blockLength += newBlock.blockLength;
                return true;
            }
            else {
                return false;
            }
        }
    }
}
