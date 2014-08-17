package com.ess.jloader.packer;

import com.ess.jloader.utils.BitOutputStream;
import com.ess.jloader.utils.Utils;

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
        Utils.writeSmallShort3(out, constIndex);
    }
}
