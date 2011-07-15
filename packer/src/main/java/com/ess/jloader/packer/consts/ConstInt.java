package com.ess.jloader.packer.consts;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class ConstInt extends Const {

    private int value;

    public ConstInt(DataInput in) throws IOException {
        value = in.readInt();
    }

    public int getValue() {
        return value;
    }

    @Override
    public byte getCode() {
        return 3;
    }
}
