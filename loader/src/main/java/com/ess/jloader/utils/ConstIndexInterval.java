package com.ess.jloader.utils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class ConstIndexInterval {

    private final int firstIndex;
    private final int count;

    private final int bitsCount;

    public ConstIndexInterval(ConstIndexInterval rightNeighbor, int count) {
        this(rightNeighbor.firstIndex - count, count);
    }

    public ConstIndexInterval(int firstIndex, int count) {
        this.firstIndex = firstIndex;
        this.count = count;

        bitsCount = 32 - Integer.numberOfLeadingZeros(count - 1);
    }

    public int getFirstIndex() {
        return firstIndex;
    }

    public int getCount() {
        return count;
    }

    public int readIndexCompact(BitInputStream in) throws IOException {
        return in.readBits(bitsCount) + firstIndex;
    }

    public int readIndexCompact(DataInputStream in) throws IOException {
        return Utils.readLimitedShort(in, count - 1) + firstIndex;
    }

    public void writeIndexCompact(BitOutputStream out, int index) throws IOException {
        assert index >= firstIndex && index < firstIndex + count;

        out.writeBits(index - firstIndex, bitsCount);
    }

    public void writeIndexCompact(DataOutputStream out, int index) throws IOException {
        assert index >= firstIndex && index < firstIndex + count;

        Utils.writeLimitedNumber(out, index - firstIndex, count - 1);
    }

    public static ConstIndexInterval create(int rightBorder, int count) {
        return new ConstIndexInterval(rightBorder - count, count);
    }

    @Override
    public String toString() {
        if (count == 0) {
            return "(" + firstIndex + ")";
        }
        return "[" + firstIndex + " - " + (firstIndex + count - 1) + "]";
    }
}
