package com.ess.jloader.packer.consts;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class ConstMethod extends ConstRef {
    public ConstMethod(DataInput in) throws IOException {
        super(in);
    }

    @Override
    public byte getCode() {
        return 10;
    }
}
