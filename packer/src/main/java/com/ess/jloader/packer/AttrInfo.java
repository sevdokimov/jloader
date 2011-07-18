package com.ess.jloader.packer;

import com.ess.jloader.packer.consts.CRef;
import com.ess.jloader.packer.consts.ConstUft;

import java.io.DataInput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class AttrInfo {

    private CRef<ConstUft> name;
    private byte[] data;

    private final AClass aClass;

    public AttrInfo(AClass aClass, DataInput in) throws IOException {
        this.aClass = aClass;
        name = aClass.createRef(ConstUft.class, in);

        int length = in.readInt();

        data = new byte[length];
        in.readFully(data);
    }

    public CRef<ConstUft> getName() {
        return name;
    }

    public AClass getaClass() {
        return aClass;
    }

    public byte[] getData() {
        return data;
    }

    public static List<AttrInfo> readAttrs(AClass aClass, DataInput in) throws IOException {
        int attrCount = in.readUnsignedShort();
        List<AttrInfo> res = new ArrayList<AttrInfo>();
        for (int i = 0; i < attrCount; i++) {
            res.add(new AttrInfo(aClass, in));
        }

        return res;
    }
}
