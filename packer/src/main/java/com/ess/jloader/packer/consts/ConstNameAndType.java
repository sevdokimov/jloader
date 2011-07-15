package com.ess.jloader.packer.consts;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class ConstNameAndType extends Const {

    private int nameIndex;
    private int typeIndex;

    public ConstNameAndType(DataInput in) throws IOException {
        nameIndex = in.readUnsignedShort();
        typeIndex = in.readUnsignedShort();
    }

    public int getNameIndex() {
        return nameIndex;
    }

    public int getTypeIndex() {
        return typeIndex;
    }

    @Override
    public byte getCode() {
        return 12;
    }
}
