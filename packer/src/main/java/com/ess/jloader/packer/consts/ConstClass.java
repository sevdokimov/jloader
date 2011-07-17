package com.ess.jloader.packer.consts;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class ConstClass extends Const {

    private int typeIndex;

    public ConstClass(DataInput in) throws IOException {
        typeIndex = in.readUnsignedShort();
    }

    public int getTypeIndex() {
        return typeIndex;
    }

    @Override
    public byte getCode() {
        return 7;
    }
}
