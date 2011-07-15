package com.ess.jloader.packer;

import java.io.DataInput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class AttrInfo {

    private int nameIndex;
    private byte[] data;

    public AttrInfo(DataInput in) throws IOException {
        nameIndex = in.readUnsignedShort();
        int length = in.readInt();

        data = new byte[length];
        in.readFully(data);
    }

    public int getNameIndex() {
        return nameIndex;
    }

    public byte[] getData() {
        return data;
    }

    public static List<AttrInfo> readAttrs(DataInput in) throws IOException {
        int attrCount = in.readUnsignedShort();
        List<AttrInfo> res = new ArrayList<AttrInfo>();
        for (int i = 0; i < attrCount; i++) {
            res.add(new AttrInfo(in));
        }

        return res;
    }
}
