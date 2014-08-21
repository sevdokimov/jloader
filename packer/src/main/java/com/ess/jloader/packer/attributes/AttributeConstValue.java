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
public class AttributeConstValue extends Attribute {

    private final int constIndex;

    public AttributeConstValue(ByteBuffer buffer) {
        super("ConstantValue");

        int length = buffer.getInt();
        assert length == 2;

        constIndex = buffer.getShort() & 0xFFFF;
    }

    @Override
    public void writeTo(DataOutputStream out, BitOutputStream bitOut, ClassDescriptor descriptor) throws IOException {
        PackUtils.writeSmallShort3(out, constIndex);
    }
}
