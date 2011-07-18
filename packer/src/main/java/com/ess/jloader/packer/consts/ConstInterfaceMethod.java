package com.ess.jloader.packer.consts;

import com.ess.jloader.packer.AClass;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class ConstInterfaceMethod extends ConstRef {
    public ConstInterfaceMethod(AClass aClass, DataInput in) throws IOException {
        super(aClass, in);
    }

    @Override
    public byte getCode() {
        return 11;
    }
}
