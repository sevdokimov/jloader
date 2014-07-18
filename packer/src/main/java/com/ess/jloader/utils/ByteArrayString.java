package com.ess.jloader.utils;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * @author Sergey Evdokimov
 */
public class ByteArrayString {

    private final byte[] data;

    private final int offset;

    private final int length;

    private int hashCode;

    public ByteArrayString(byte[] data) {
        this(data, 0, data.length);
    }

    public ByteArrayString(byte[] data, int offset, int length) {
        assert data.length >= offset + length;

        this.data = data;
        this.offset = offset;
        this.length = length;
    }

    public byte[] getData() {
        return data;
    }

    public int getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }

    public int getEnd() {
        return offset + length;
    }

    public static ByteArrayString fromArray(byte[] data, int start, int end) {
        assert end >= start;
        return new ByteArrayString(data, start, end - start);
    }

    public byte byteAt(int index) {
        return data[offset + index];
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ByteArrayString)) return false;

        ByteArrayString that = (ByteArrayString) o;

        if (length != that.length) return false;

        for (int i = 0; i < length; i++) {
            if (data[offset + i] != that.data[that.offset + i]) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = hashCode;
        if (result == 0) {
            for (int i = 0; i < length; i++) {
                result = result * 31 + data[offset + i];
            }

            hashCode = result;
        }

        return result;
    }

    public void writeTo(OutputStream out) throws IOException {
        out.write(data, offset, length);
    }

    @Override
    public String toString() {
        StringBuilder res = new StringBuilder();
        res.append('(').append(length).append(')');

        for (int i = 0; i < length; i++) {
            char a = (char) (byteAt(i) & 0xFF);
            if (a <= 32) {
                res.append('#').append(Integer.toHexString(a));
            }
            else {
                res.append(a);
            }
        }

        return res.toString();
    }
}
