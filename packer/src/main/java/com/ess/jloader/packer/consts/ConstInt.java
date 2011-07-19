package com.ess.jloader.packer.consts;

import com.ess.jloader.packer.AClass;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class ConstInt extends Const {

    private int value;

    public ConstInt(AClass aClass, DataInput in) throws IOException {
        value = in.readInt();
    }

    public int getValue() {
        return value;
    }

    @Override
    public byte getCode() {
        return 3;
    }

    @Override
    public void writeTo(DataOutput out) throws IOException {
        out.writeInt(value);
    }
}
