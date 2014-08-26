package com.ess.jloader.utils;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class FastBuffer {

    public final byte[] array;

    public int pos;

    public FastBuffer(int size) {
        array = new byte[size];
    }


    public void putInt(int value) {
        putInt(pos, value);
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
        index += 3;
        array[index--] = (byte) value;

        value >>>= 8;
        array[index--] = (byte) value;

        value >>>= 8;
        array[index--] = (byte) value;

        value >>>= 8;
        array[index] = (byte) value;
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
        return ((array[pos] & 0xFF) << 8) | (array[pos + 1] & 0xFF);
    }

    public void readFully(DataInputStream in, int len) throws IOException {
        in.readFully(array, pos, len);
        pos += len;
    }
}
