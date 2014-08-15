package com.ess.jloader.packer;

import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class LimitNumberWriter {

    private int limit;
    private int bitCount;

    private LimitNumberWriter(int limit, int bitCount) {
        this.limit = limit;
        this.bitCount = bitCount;
    }

    public void write(BitOutputStream out, int x) throws IOException {
        assert x <= limit;

        out.writeBits(x, bitCount);
    }

    public static LimitNumberWriter create(int limit) {
        int bitCount = 32 - Integer.numberOfLeadingZeros(limit);
        return new LimitNumberWriter(limit, bitCount);
    }
}
