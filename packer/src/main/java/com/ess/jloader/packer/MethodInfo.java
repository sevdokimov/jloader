package com.ess.jloader.packer;

import java.io.DataInput;
import java.io.IOException;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class MethodInfo {

    private int accessFlag;
    private int nameIndex;
    private int descriptorIndex;
    private List<AttrInfo> attrs;

    public MethodInfo(DataInput in) throws IOException {
        accessFlag = in.readUnsignedShort();
        nameIndex = in.readUnsignedShort();
        descriptorIndex = in.readUnsignedShort();
        attrs = AttrInfo.readAttrs(in);
    }

    public int getAccessFlag() {
        return accessFlag;
    }

    public int getNameIndex() {
        return nameIndex;
    }

    public int getDescriptorIndex() {
        return descriptorIndex;
    }

    public List<AttrInfo> getAttrs() {
        return attrs;
    }
}
