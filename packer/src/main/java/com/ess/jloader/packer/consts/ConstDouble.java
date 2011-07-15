package com.ess.jloader.packer.consts;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class ConstDouble extends Const {

    private double value;

    public ConstDouble(DataInput in) throws IOException {
        value = in.readDouble();
    }

    public double getValue() {
        return value;
    }

    @Override
    public byte getCode() {
        return 6;
    }
}
