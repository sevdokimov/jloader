package com.ess.jloader.packer;

import com.ess.jloader.utils.BitOutputStream;
import org.jetbrains.annotations.Nullable;

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

    public static final AttributeFactory FACTORY = new AttributeFactory() {
        @Nullable
        @Override
        public Attribute read(ClassDescriptor descriptor, String name, ByteBuffer buffer) {
            if (!name.equals("Signature")) return null;

            return new AttributeSignature(buffer);
        }
    };
}
