package cn.bs352.jrdiff.generator;

import java.io.*;
import java.security.DigestException;
import java.security.NoSuchAlgorithmException;

/**
 * This tool is an adaptation of the rsync algorithm
 * <p/>
 * rdiff utility has 3 usages:
 * <p/>
 * 1. Generate signature data from new file
 * 2. Generate delta data between old and new file using signature data.
 * 3. Applying delta file on old file to get new file.
 *
 * @author bo.shen
 */
public class RDiffGenerator {

    protected static final int BLOCK_LENGTH = 2048;
    protected static final int STRONG_HASH_LENGTH = 8;

    protected static final int SIG_MAGIC = 0x72730136;
    protected static final int DELTA_MAGIC = 0x72730236;

    protected static final byte[] FLAG_DATA = new byte[]{0, 0x41, 0x42, 0, 0x44};

    // Reference FLAGS, _4 means an offset can be stored in an integer, otherwise long is used
    protected static final byte[] FLAG_REF_4 = new byte[]{0, 0x51, 0x52, 0, 0x54};
    protected static final byte[] FLAG_REF_8 = new byte[]{0, 0x61, 0x62, 0, 0x64};

    /**
     * Generate Signature is a process of analysing the base file
     * <p/>
     * Base file is split into a series of non-overlapping fixed-size blocks of bytes.
     * A RollingChecksum and MD5 checksum is calculated for each block
     * <p/>
     * Result is stored in a signature file
     *
     * @param in  base File to analyse
     * @param out signature output
     */
    public void generateSignature(InputStream in, OutputStream out) throws IOException, NoSuchAlgorithmException {
        RDiffSignatureGenerator generator = new RDiffSignatureGenerator(BLOCK_LENGTH, STRONG_HASH_LENGTH);
        generator.generate(in, out);
    }

    /**
     * Generate Delta is a process of creating the actual patch file required to transform old to new
     *
     * @param sigFile Generated signature file
     * @param newFile New version of base file
     * @param delta   Delta Output that can be used to transform base file into new version
     */
    public void generateDeltaFromSig(InputStream sigFile, InputStream newFile, OutputStream delta) throws IOException, NoSuchAlgorithmException, DigestException {
        RDiffDeltaGenerator generator = new RDiffDeltaGenerator();
        generator.generate(sigFile, newFile, delta);
    }

    /**
     * Convenient method of skipping the intermediate step of generating signature.
     * Signatures are stored in memory temporarily, discarded after delta is created.
     *
     * @param baseFile Base file
     * @param newFile  New version of Base file
     * @param delta    Delta Output that can be used to transform base file into new version
     */
    public void generateDelta(InputStream baseFile, InputStream newFile, OutputStream delta) throws IOException, NoSuchAlgorithmException, DigestException {
        ByteArrayOutputStream sig = new ByteArrayOutputStream();
        generateSignature(baseFile, sig);

        generateDeltaFromSig(new ByteArrayInputStream(sig.toByteArray()), newFile, delta);
    }

}
