package com.ess.jloader.utils;

import java.io.ByteArrayOutputStream;

/**
 * @author Sergey Evdokimov
 */
public class OpenByteOutputStream extends ByteArrayOutputStream {

    public OpenByteOutputStream() {
    }

    public OpenByteOutputStream(int size) {
        super(size);
    }

    public byte[] getBuffer() {
        return buf;
    }

    public static OpenByteOutputStream wrap(byte[] buffer, int position) {
        OpenByteOutputStream res = new OpenByteOutputStream(0);
        res.buf = buffer;
        res.count = position;
        return res;
    }
}