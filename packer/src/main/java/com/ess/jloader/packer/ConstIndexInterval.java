package com.ess.jloader.packer;

import com.ess.jloader.utils.BitInputStream;
import com.ess.jloader.utils.BitOutputStream;
import com.ess.jloader.utils.Utils;

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

    public void writeIndexCompactNullable(BitOutputStream bitOut, int index) throws IOException {
        assert index == 0 || (index >= firstIndex && index < firstIndex + count);

        int bitCount = 32 - Integer.numberOfLeadingZeros(count);

        if (index == 0) {
            bitOut.writeBits(0, bitCount);
        }
        else {
            bitOut.writeBits(index - firstIndex + 1, bitCount);
        }
    }

    public void writeIndexCompactNullable(DataOutputStream out, int index) throws IOException {
        assert index == 0 || (index >= firstIndex && index < firstIndex + count);

        if (index == 0) {
            PackUtils.writeLimitedNumber(out, 0, count);
        }
        else {
            PackUtils.writeLimitedNumber(out, index + 1 - firstIndex, count);
        }
    }

    public void writeIndexCompact(DataOutputStream out, int index) throws IOException {
        assert index >= firstIndex && index < firstIndex + count;

        PackUtils.writeLimitedNumber(out, index - firstIndex, count - 1);
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
