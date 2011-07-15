package com.ess.jloader.packer.consts;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class ConstString extends Const {

    private int utfIndex;

    public ConstString(DataInput in) throws IOException {
        utfIndex = in.readUnsignedShort();
    }

    public int getUtfIndex() {
        return utfIndex;
    }

    @Override
    public byte getCode() {
        return 8;
    }
}
