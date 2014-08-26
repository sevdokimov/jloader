package com.ess.jloader.utils;

import java.io.*;

/**
 * @author Sergey Evdokimov
 */
public class BitOutputStream extends FilterOutputStream implements DataOutput {

    private int x;
    private int writtenBits;

    private boolean finished;

    private final DataOutputStream dataOutput = new DataOutputStream(this);

    public BitOutputStream(OutputStream out) {
        super(out);
    }

    @Override
    public void write(int b) throws IOException {
        writeBits(b, 8);
    }

    public void writeBit(boolean value) throws IOException {
        writeBit(value ? 1 : 0);
    }

    public void writeBit(int value) throws IOException {
        writeBits(value, 1);
    }

    public void writeBits(int value, int bitCount) throws IOException {
        assert !finished;
        assert bitCount <= 16;

        x |= ((value & ((1 << bitCount) - 1)) << writtenBits);
        writtenBits += bitCount;

        while (writtenBits >= 8) {
            out.write(x);
            x >>>= 8;
            writtenBits -= 8;
        }
    }

    public void writeSmall_0_3_8_16(int value) throws IOException {
        int x = value;
        if (x == 0) {
            writeBit(0);
            return;
        }

        writeBit(1);

        x--;

        if (x < 7) {
            writeBits(x, 3);
            return;
        }
        writeBits(7, 3);

        x -= 7;

        if (x < 0xFF) {
            writeByte(x);
            return;
        }
        writeByte(0xFF);

        writeShort(value);
    }

    public void writeSmall2__0_1_4_8(int x) throws IOException {
        assert x < (1 + (1 << 1) + (1 << 4) + (1 << 8));

        if (x == 0) {
            writeBits(0, 2);
            return;
        }
        x--;

        if (x < (1 << 1)) {
            writeBits(1, 2);
            writeBits(x, 1);
            return;
        }

        x -= (1 << 1);

        if (x < (1 << 4)) {
            writeBits(2, 2);
            writeBits(x, 4);
            return;
        }

        x -= (1 << 4);

        writeBits(3, 2);
        writeBits(x, 8);
    }

    public void writeSmall_3_8(int x) throws IOException {
        if (x < 7) {
            writeBits(x, 3);
            return;
        }
        writeBits(7, 3);
        x -= 7;

        assert x < 0x100;
        writeByte(x);
    }

    @Override
    public void flush() throws IOException {

    }

    public void finish() throws IOException {
        if (writtenBits > 0) {
            out.write(x);
        }
        finished = true;
    }

    @Override
    public void writeBoolean(boolean v) throws IOException {
        writeBit(v);
    }

    @Override
    public void writeByte(int value) throws IOException {
        x |= ((value & 0xFF) << writtenBits);

        out.write(x);
        x >>>= 8;
    }

    @Override
    public void writeShort(int value) throws IOException {
        assert !finished;

        x |= ((value & 0xFFFF) << writtenBits);

        out.write(x);
        x >>>= 8;
        out.write(x);
        x >>>= 8;
    }

    @Override
    public void writeChar(int value) throws IOException {
        writeShort(value);
    }

    @Override
    public void writeInt(int value) throws IOException {
        assert !finished;

        x |= ((value & 0xFFFFFF) << writtenBits);

        out.write(x);
        x >>>= 8;
        out.write(x);
        x >>>= 8;
        out.write(x);
        x >>>= 8;

        x |= ((value >>> 24) << writtenBits);
        out.write(x);
        x >>>= 8;
    }

    @Override
    public void writeLong(long v) throws IOException {
        dataOutput.writeLong(v);
    }

    @Override
    public void writeFloat(float v) throws IOException {
        dataOutput.writeFloat(v);
    }

    @Override
    public void writeDouble(double v) throws IOException {
        dataOutput.writeDouble(v);
    }

    @Override
    public void writeBytes(String s) throws IOException {
        dataOutput.writeBytes(s);
    }

    @Override
    public void writeChars(String s) throws IOException {
        dataOutput.writeChars(s);
    }

    @Override
    public void writeUTF(String s) throws IOException {
        dataOutput.writeUTF(s);
    }

    public void writeLimitedShort(int value, int limit) throws IOException {
        assert value <= limit;

        if (Utils.CHECK_LIMITS) {
            writeInt(limit);
        }

        int bitCount = 32 - Integer.numberOfLeadingZeros(limit);
        writeBits(value, bitCount);
    }

    public OutputStream getDelegate() {
        return out;
    }
}
