package com.ess.jloader.packer.consts;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class ConstFloat extends Const {

    private float value;

    public ConstFloat(DataInput in) throws IOException {
        value = in.readFloat();
    }

    public float getValue() {
        return value;
    }

    @Override
    public byte getCode() {
        return 4;
    }
}
