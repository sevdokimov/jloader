package com.ess.jloader.utils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class ConstIndexInterval {

    public final int firstIndex;
    public final int count;

    public ConstIndexInterval(ConstIndexInterval rightNeighbor, int count) {
        this(rightNeighbor.firstIndex - count, count);
    }

    public ConstIndexInterval(int firstIndex, int count) {
        this.firstIndex = firstIndex;
        this.count = count;
    }

    public int readIndexCompact(BitInputStream in) throws IOException {
        return in.readBits(32 - Integer.numberOfLeadingZeros(count - 1)) + firstIndex;
    }

    public int readIndexCompactNullable(BitInputStream in) throws IOException {
        int x = in.readBits(32 - Integer.numberOfLeadingZeros(count));
        if (x == 0) {
            return x;
        }

        return x - 1 + firstIndex;
    }

    public int readIndexCompact(DataInputStream in) throws IOException {
        return Utils.readLimitedShort(in, count - 1) + firstIndex;
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
