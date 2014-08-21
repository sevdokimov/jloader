package com.ess.jloader.utils;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;

/**
 * @author Sergey Evdokimov
 */
public class OpenByteOutputStream extends ByteArrayOutputStream {

    public OpenByteOutputStream() {
    }

    public OpenByteOutputStream(int size) {
        super(size);
    }

    public OpenByteOutputStream(byte[] buffer) {
        this(buffer, 0);
    }

    public OpenByteOutputStream(byte[] buffer, int position) {
        super(0);
        this.buf = buffer;
        count = position;
    }

    public byte[] getBuffer() {
        return buf;
    }

    public void writeTo(ByteBuffer buffer) {
        buffer.put(buf, 0, count);
    }

    public void setPosition(int position) {
        count = position;
    }
}
