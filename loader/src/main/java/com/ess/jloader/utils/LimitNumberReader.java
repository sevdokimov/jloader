package com.ess.jloader.utils;

import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class LimitNumberReader {

    private final int limit;
    private final int bitCount;

    public LimitNumberReader(int limit) {
        this.limit = limit;
        bitCount = 32 - Integer.numberOfLeadingZeros(limit);
    }

    public int read(BitInputStream in) throws IOException {
        return in.readBits(bitCount);
    }
}
