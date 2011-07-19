package com.ess.jloader.packer.consts;

import com.ess.jloader.packer.AClass;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class ConstString extends Const {

    private CRef<ConstUtf> utf;

    public ConstString(AClass aClass, DataInput in) throws IOException {
        utf = aClass.createRef(ConstUtf.class, in);
    }

    public CRef<ConstUtf> getUtf() {
        return utf;
    }

    @Override
    public byte getCode() {
        return 8;
    }

    @Override
    public void writeTo(DataOutput out) throws IOException {
        utf.writeTo(out);
    }
}
