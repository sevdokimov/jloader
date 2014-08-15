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

    public DataInputStream getDataInputStream() {
        assert size() == delegate.size();
        return new DataInputStream(new ByteArrayInputStream(delegate.getBuffer(), 0, delegate.size()));
    }
}
