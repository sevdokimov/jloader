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
public class AttributeUnknown extends Attribute {

    private final int length;

    private final byte[] body;

    public AttributeUnknown(String name, ByteBuffer buffer) {
        super(name);

        length = buffer.getInt();
        body = PackUtils.readBytes(buffer, length);
    }


    @Override
    public void writeTo(DataOutputStream out, BitOutputStream bitOut, ClassDescriptor descriptor) throws IOException {
        out.writeInt(length);
        out.write(body);
    }
}
