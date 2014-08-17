package com.ess.jloader.packer;

import com.ess.jloader.utils.BitOutputStream;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author Sergey Evdokimov
 */
public class AttributeSignature extends Attribute {

    private int utfIndex;

    public AttributeSignature(ByteBuffer buffer) {
        super("Signature");

        int length = buffer.getInt();
        assert length == 2;

        utfIndex = buffer.getShort();
    }

    @Override
    public void writeTo(DataOutputStream out, BitOutputStream bitOut, ClassDescriptor descriptor) throws IOException {
        descriptor.getUtfInterval().writeIndexCompact(out, utfIndex);
    }
}
