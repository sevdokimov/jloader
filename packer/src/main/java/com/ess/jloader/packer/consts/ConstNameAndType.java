package com.ess.jloader.packer.consts;

import com.ess.jloader.packer.AClass;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class ConstNameAndType extends Const {

    private CRef<ConstUtf> name;
    private CRef<ConstUtf> type;

    public ConstNameAndType(AClass aClass, DataInput in) throws IOException {
        name = aClass.createRef(ConstUtf.class, in);
        type = aClass.createRef(ConstUtf.class, in);
    }

    public CRef<ConstUtf> getName() {
        return name;
    }

    public CRef<ConstUtf> getType() {
        return type;
    }

    @Override
    public byte getCode() {
        return 12;
    }

    @Override
    public void writeTo(DataOutput out) throws IOException {
        name.writeTo(out);
        type.writeTo(out);
    }
}
