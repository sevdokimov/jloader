package com.ess.jloader.utils;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author Sergey Evdokimov
 */
public class FastBuffer {

    public final byte[] array;

    public int pos;

    private final ByteBuffer bb;

    public FastBuffer(int size) {
        array = new byte[size];
        bb = ByteBuffer.wrap(array);
    }


    public void putInt(int value) {
        bb.putInt(pos, value);
        pos += 4;
    }

    public void put(int value) {
        array[pos++] = (byte) value;
    }

    public void put(byte[] data) {
        if (data.length < 16) {
            int p = pos;
            for (int i = 0; i < data.length; i++) {
                array[p++] = data[i];
            }
            pos = p;
        }
        else {
            System.arraycopy(data, 0, array, pos, data.length);
            pos += data.length;
        }
    }

    public void putInt(int index, int value) {
        bb.putInt(index, value);
    }

    public void putShort(int index, int value) {
        array[index] = (byte) (value >>> 8);
        array[index + 1] = (byte) value;
    }

    public void putShort(int value) {
        array[pos] = (byte) (value >>> 8);
        array[pos + 1] = (byte) value;

        pos += 2;
    }

    public void skip(int n) {
        pos += n;
    }

    public int getShort(int pos) {
        return bb.getShort(pos);
    }

    public void readFully(DataInputStream in, int len) throws IOException {
        in.readFully(array, pos, len);
        pos += len;
    }
}
