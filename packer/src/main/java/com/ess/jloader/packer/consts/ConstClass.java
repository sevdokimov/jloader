package com.ess.jloader.packer.consts;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class ConstClass extends Const {

    private int nameIndex;

    public ConstClass(DataInput in) throws IOException {
        nameIndex = in.readUnsignedShort();
    }

    public int getNameIndex() {
        return nameIndex;
    }

    @Override
    public byte getCode() {
        return 7;
    }
}
