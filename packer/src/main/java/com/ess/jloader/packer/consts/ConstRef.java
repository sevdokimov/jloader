package com.ess.jloader.packer.consts;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public abstract class ConstRef extends Const {

    private int classIndex;
    private int nameIndex;

    public ConstRef(DataInput in) throws IOException {
        classIndex = in.readUnsignedShort();
        nameIndex = in.readUnsignedShort();
    }

}
