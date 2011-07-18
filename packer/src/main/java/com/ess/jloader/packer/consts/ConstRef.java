package com.ess.jloader.packer.consts;

import com.ess.jloader.packer.AClass;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public abstract class ConstRef extends Const {

    private CRef<ConstClass> containingClass;
    private CRef<ConstNameAndType> nameAndType;

    public ConstRef(AClass aClass, DataInput in) throws IOException {
        containingClass = aClass.createRef(ConstClass.class, in);
        nameAndType = aClass.createRef(ConstNameAndType.class, in);
    }

    public CRef<ConstClass> getContainingClass() {
        return containingClass;
    }

    public CRef<ConstNameAndType> getNameAndType() {
        return nameAndType;
    }
}
