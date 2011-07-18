package com.ess.jloader.packer;

import com.ess.jloader.packer.consts.CRef;
import com.ess.jloader.packer.consts.ConstUft;

import java.io.DataInput;
import java.io.IOException;
import java.util.List;

import static java.lang.reflect.Modifier.*;

/**
 * @author Sergey Evdokimov
 */
public class FiledInfo {

    public static final int MODIFIER_MASK = PUBLIC + PRIVATE + PROTECTED + STATIC + FINAL + VOLATILE + TRANSIENT;

    private int accessFlag;

    private CRef<ConstUft> name;
    private CRef<ConstUft> descriptor;
    private List<AttrInfo> attrs;

    private final AClass aClass;

    public FiledInfo(AClass aClass, DataInput in) throws IOException {
        this.aClass = aClass;

        accessFlag = in.readUnsignedShort();
        if ((accessFlag & MODIFIER_MASK) != 0) throw new InvalidClassException();

        name = aClass.createRef(ConstUft.class, in);
        descriptor = aClass.createRef(ConstUft.class, in);
        attrs = AttrInfo.readAttrs(aClass, in);
    }

    public int getAccessFlag() {
        return accessFlag;
    }

    public CRef<ConstUft> getName() {
        return name;
    }

    public CRef<ConstUft> getDescriptor() {
        return descriptor;
    }

    public AClass getaClass() {
        return aClass;
    }

    public List<AttrInfo> getAttrs() {
        return attrs;
    }
}
