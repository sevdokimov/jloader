package com.ess.jloader.packer.consts;

import com.ess.jloader.packer.AClass;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class ConstLong extends Const {

    private long value;

    public ConstLong(AClass aClass, DataInput in) throws IOException {
        value = in.readLong();
    }

    public long getValue() {
        return value;
    }

    @Override
    public byte getCode() {
        return 5;
    }

    @Override
    public boolean isGet2ElementsInPool() {
        return true;
    }
}
