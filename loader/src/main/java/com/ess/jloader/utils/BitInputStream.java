package com.ess.jloader.utils;

import com.ess.jloader.loader.PackClassLoader;

import java.io.*;

/**
 * @author Sergey Evdokimov
 */
public class BitInputStream extends FilterInputStream implements DataInput {

    private int x;
    private int remainBits;

    private final DataInputStream dataIn = new DataInputStream(this);

    public BitInputStream(InputStream in) {
        super(in);
    }

    @Override
    public int read() throws IOException {
        return readBitsSoft(8);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (len == 0) return 0;

        int read = in.read(b, off, len);
        if (read < 0) {
            return -1;
        }

        if (remainBits == 0) {
            return read;
        }

        int z = x;
        int end = off + read;
        for (int i = off; i != end; i++) {
            z = ((b[i] & 0xFF) << remainBits) | z;
            b[i] = (byte) z;
            z >>>= 8;
        }
        x = z;

        return read;
    }

    public int readBitsSoft(int bitCount) throws IOException {
        while (bitCount > remainBits) {
            int value = in.read();
            if (value == -1) {
                if (remainBits == 0) {
                    return -1;
                }
                else {
                    throw new EOFException();
                }
            }

            x |= (value << remainBits);
            remainBits += 8;
        }

        int res = x & ((1 << bitCount) - 1);  // optimize???
        x >>>= bitCount;
        remainBits -= bitCount;

        return res;

    }

    public int readBits(int bitCount) throws IOException {
        while (bitCount > remainBits) {
            int value = in.read();
            if (value == -1) throw new EOFException();

            x |= (value << remainBits);
            remainBits += 8;
        }

        int res = x & ((1 << bitCount) - 1);  // optimize???
        x >>>= bitCount;
        remainBits -= bitCount;

        return res;
    }

    @Override
    public long skip(long n) throws IOException {
        throw new UnsupportedOperationException();
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

    public int readBit() throws IOException {
        if (remainBits == 0) {
            int value = in.read();
            if (value == -1) throw new EOFException();

            int res = value & 1;

            x = value >>> 1;
            remainBits = 7;
            return res;
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
        int value = in.read();
        if (value == -1) throw new EOFException();

        x |= (value << remainBits);

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
        int value = in.read();
        if (value == -1) throw new EOFException();

        x |= (value << remainBits);

        value = in.read();
        if (value == -1) throw new EOFException();

        x |= (value << (remainBits + 8));

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
        int value = in.read();
        if (value == -1) throw new EOFException();
        x |= (value << remainBits);

        value = in.read();
        if (value == -1) throw new EOFException();
        x |= (value << (remainBits + 8));

        value = in.read();
        if (value == -1) throw new EOFException();
        x |= (value << (remainBits + 16));

        int res = x & 0xFFFFFF;
        x >>>= 24;

        value = in.read();
        if (value == -1) throw new EOFException();
        x |= (value << remainBits);

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
