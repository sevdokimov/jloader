package com.ess.jloader.packer.consts;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class ConstInterfaceMethod extends ConstRef {
    public ConstInterfaceMethod(DataInput in) throws IOException {
        super(in);
    }

    @Override
    public byte getCode() {
        return 11;
    }
}
