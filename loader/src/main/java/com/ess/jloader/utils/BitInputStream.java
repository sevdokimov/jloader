package com.ess.jloader.utils;

import java.io.*;

/**
 * @author Sergey Evdokimov
 */
public class BitInputStream extends InputStream implements DataInput {

    private int x;
    private int remainBits;

    private final DataInputStream dataIn = new DataInputStream(this);

    private final byte[] buffer;
    private final int limit;

    private int pos;

    public BitInputStream(byte[] buffer, int pos, int limit) {
        this.buffer = buffer;
        this.limit = limit;

        this.pos = pos;

        assert limit <= buffer.length;
        assert pos >= 0 && pos <= limit;
    }

    @Override
    public int read() throws IOException {
        return readBitsSoft(8);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (len == 0) return 0;

        if (pos == limit) return -1;

        int end = off + len;
        do {
            x |= (buffer[pos++] & 0xFF) << remainBits;
            b[off++] = (byte) x;

            x >>>= 8;
        }
        while (off < end && pos < limit);

        return off - (end - len);
    }

    public int readBitsSoft(int bitCount) throws IOException {
        while (bitCount > remainBits) {
            if (pos == limit) {
                if (remainBits == 0) {
                    return -1;
                }
                else {
                    throw new EOFException();
                }
            }

            x |= ((buffer[pos++] & 0xFF) << remainBits);
            remainBits += 8;
        }

        int res = x & ((1 << bitCount) - 1);  // optimize???
        x >>>= bitCount;
        remainBits -= bitCount;

        return res;
    }

    public int readBits(int bitCount) throws IOException {
        while (bitCount > remainBits) {
            if (pos == limit) throw new EOFException();

            x |= ((buffer[pos++] & 0xFF) << remainBits);
            remainBits += 8;
        }

        int res = x & ((1 << bitCount) - 1);
        x >>>= bitCount;
        remainBits -= bitCount;

        return res;
    }

    @Override
    public long skip(long n) throws IOException {
        throw new UnsupportedOperationException();
    }

    public int readLimitedShort(int limit) throws IOException {
        int bitsCount = 32 - Integer.numberOfLeadingZeros(limit);
        return readBits(bitsCount);
    }

    public int readSmall_0_3_8_16() throws IOException {
        if (readBit() == 0) {
            return 0;
        }

        int res = readBits(3);
        if (res < 7) {
            return res + 1;
        }

        res = readBits(8);
        if (res < 0xFF) {
            return res + 1 + 7;
        }

        return readUnsignedShort();
    }

    public int readSmall2__0_1_4_8() throws IOException {
        int z = readBits(2);

        if (z == 0) {
            return 0;
        }
        if (z == 1) {
            return readBits(1) + 1;
        }
        if (z == 2) {
            return readBits(4) + 1 + (1 << 1);
        }

        return readBits(8) + 1 + (1 << 1) + (1 << 4);
    }

    public int readSmall_3_8() throws IOException {
        int res = readBits(3);
        if (res < 7) {
            return res;
        }

        return readByte() + 7;
    }

    public int readBit() throws IOException {
        if (remainBits == 0) {
            if (pos == limit) throw new EOFException();

            x = buffer[pos++] & 0xFF;
            remainBits = 8;
        }

        int res = x & 1;
        x >>>= 1;
        remainBits--;

        return res;
    }

    @Override
    public void readFully(byte[] b) throws IOException {
        readFully(b, 0, b.length);
    }

    @Override
    public void readFully(byte[] b, int off, int len) throws IOException {
        dataIn.readFully(b, off, len);
    }


    @Override
    public int skipBytes(int n) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean readBoolean() throws IOException {
        return readBit() > 0;
    }

    @Override
    public byte readByte() throws IOException {
        return (byte) readUnsignedByte();
    }

    @Override
    public int readUnsignedByte() throws IOException {
        if (pos == limit) throw new EOFException();

        x |= ((buffer[pos++] & 0xFF) << remainBits);

        int res = x & 0xFF;
        x >>>= 8;

        return res;
    }

    @Override
    public short readShort() throws IOException {
        return (short) readUnsignedShort();
    }

    public int readShortBE() throws IOException {
        return dataIn.readUnsignedShort();
    }

    public int readIntBE() throws IOException {
        return dataIn.readInt();
    }

    @Override
    public int readUnsignedShort() throws IOException {
        if (pos + 2 > limit) throw new EOFException();

        x |= ((buffer[pos++] & 0xFF) << remainBits);

        x |= ((buffer[pos++] & 0xFF) << (remainBits + 8));

        int res = x & 0xFFFF;
        x >>>= 16;

        return res;
    }

    @Override
    public char readChar() throws IOException {
        return (char) dataIn.readShort();
    }

    @Override
    public int readInt() throws IOException {
        if (pos + 4 > limit) throw new EOFException();

        x |= ((buffer[pos++] & 0xFF) << remainBits);

        x |= ((buffer[pos++] & 0xFF) << (remainBits + 8));

        x |= ((buffer[pos++] & 0xFF) << (remainBits + 16));

        int res = x & 0xFFFFFF;
        x >>>= 24;

        x |= ((buffer[pos++] & 0xFF) << remainBits);

        res |= x << 24;

        x >>>= 8;

        return res;
    }

    @Override
    public long readLong() throws IOException {
        return dataIn.readLong();
    }

    @Override
    public float readFloat() throws IOException {
        return dataIn.readFloat();
    }

    @Override
    public double readDouble() throws IOException {
        return dataIn.readDouble();
    }

    @Override
    public String readLine() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String readUTF() throws IOException {
        return dataIn.readUTF();
    }
}
