package com.ess.jloader.packer.consts;

import com.ess.jloader.packer.AClass;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class ConstNameAndType extends Const {

    private CRef<ConstUft> name;
    private CRef<ConstUft> type;

    public ConstNameAndType(AClass aClass, DataInput in) throws IOException {
        name = aClass.createRef(ConstUft.class, in);
        type = aClass.createRef(ConstUft.class, in);
    }

    public CRef<ConstUft> getName() {
        return name;
    }

    public CRef<ConstUft> getType() {
        return type;
    }

    @Override
    public byte getCode() {
        return 12;
    }
}
