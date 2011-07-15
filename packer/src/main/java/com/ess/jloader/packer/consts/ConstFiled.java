package com.ess.jloader.packer.consts;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class ConstFiled extends ConstRef {
    public ConstFiled(DataInput in) throws IOException {
        super(in);
    }

    @Override
    public byte getCode() {
        return 9;
    }
}
