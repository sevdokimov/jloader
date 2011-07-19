package com.ess.jloader.packer.consts;

import com.ess.jloader.packer.AClass;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class ConstFloat extends Const {

    private float value;

    public ConstFloat(AClass aClass, DataInput in) throws IOException {
        value = in.readFloat();
    }

    public float getValue() {
        return value;
    }

    @Override
    public byte getCode() {
        return 4;
    }

    @Override
    public void writeTo(DataOutput out) throws IOException {
        out.writeFloat(value);
    }
}
