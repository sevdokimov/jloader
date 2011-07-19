package com.ess.jloader.packer.consts;

import com.ess.jloader.packer.AClass;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class ConstDouble extends Const {

    private double value;

    public ConstDouble(AClass aClass, DataInput in) throws IOException {
        value = in.readDouble();
    }

    public double getValue() {
        return value;
    }

    @Override
    public byte getCode() {
        return 6;
    }

    @Override
    public void writeTo(DataOutput out) throws IOException {
        out.writeDouble(value);
    }

    @Override
    public boolean isGet2ElementsInPool() {
        return true;
    }
}
