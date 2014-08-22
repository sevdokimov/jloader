package com.ess.jloader.packer.attributes;

import com.ess.jloader.packer.ClassDescriptor;
import com.ess.jloader.packer.PackUtils;
import com.ess.jloader.utils.BitOutputStream;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author Sergey Evdokimov
 */
public class AttributeStackMap extends Attribute {

    private final byte[] body;

    public AttributeStackMap(AttrContext ctx, ByteBuffer buffer) {
        super("StackMapTable");

        int attrSize = buffer.getInt();
        body = PackUtils.readBytes(buffer, attrSize);
    }

    @Override
    public void writeTo(DataOutputStream defOut, BitOutputStream bitOut, ClassDescriptor descriptor) throws IOException {
        PackUtils.writeSmallShort3(defOut, body.length);
        defOut.write(body);
    }
}
