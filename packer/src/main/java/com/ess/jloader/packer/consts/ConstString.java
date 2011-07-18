package com.ess.jloader.packer.consts;

import com.ess.jloader.packer.AClass;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class ConstString extends Const {

    private CRef<ConstUft> utf;

    public ConstString(AClass aClass, DataInput in) throws IOException {
        utf = aClass.createRef(ConstUft.class, in);
    }

    public CRef<ConstUft> getUtf() {
        return utf;
    }

    @Override
    public byte getCode() {
        return 8;
    }
}
