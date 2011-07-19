package com.ess.jloader.packer.consts;

import com.ess.jloader.packer.AClass;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class ConstClass extends Const {

    private final CRef<ConstUtf> name;

    public ConstClass(AClass aClass, DataInput in) throws IOException {
        name = aClass.createRef(ConstUtf.class, in);
    }

    public CRef<ConstUtf> getName() {
        return name;
    }

    @Override
    public byte getCode() {
        return 7;
    }

    @Override
    public void writeTo(DataOutput out) throws IOException {
        name.writeTo(out);
    }
}
