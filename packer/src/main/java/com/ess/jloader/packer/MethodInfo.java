package com.ess.jloader.packer;

import com.ess.jloader.packer.consts.CRef;
import com.ess.jloader.packer.consts.ConstUft;

import java.io.DataInput;
import java.io.IOException;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class MethodInfo {

    private int accessFlag;
    private CRef<ConstUft> name;
    private CRef<ConstUft> descriptor;
    private List<AttrInfo> attrs;

    public MethodInfo(AClass aClass, DataInput in) throws IOException {
        accessFlag = in.readUnsignedShort();
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

    public List<AttrInfo> getAttrs() {
        return attrs;
    }
}
