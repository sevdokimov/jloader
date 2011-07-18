package com.ess.jloader.packer.consts;

import com.ess.jloader.packer.AClass;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class ConstClass extends Const {

    private final CRef<ConstUft> name;

    public ConstClass(AClass aClass, DataInput in) throws IOException {
        name = aClass.createRef(ConstUft.class, in);
    }

    public CRef<ConstUft> getName() {
        return name;
    }

    @Override
    public byte getCode() {
        return 7;
    }
}
