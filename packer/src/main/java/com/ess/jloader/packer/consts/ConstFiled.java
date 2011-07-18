package com.ess.jloader.packer.consts;

import com.ess.jloader.packer.AClass;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class ConstFiled extends ConstRef {
    public ConstFiled(AClass aClass, DataInput in) throws IOException {
        super(aClass, in);
    }

    @Override
    public byte getCode() {
        return 9;
    }
}
