package com.ess.jloader.utils;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

/**
 * @author Sergey Evdokimov
 */
public class DataByteArrayOutputStream extends DataOutputStream {

    private final OpenByteOutputStream delegate;

    public DataByteArrayOutputStream() {
        super(new OpenByteOutputStream());
        delegate = (OpenByteOutputStream) out;
    }

    public byte[] getBuffer() {
        return delegate.getBuffer();
    }

    public OpenByteOutputStream getDelegate() {
        return delegate;
    }
}
