package com.ess.jloader.packer;

import org.jetbrains.annotations.Nullable;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class AttributeSignature extends Attribute {

    private int utfIndex;

    public AttributeSignature(ClassDescriptor descriptor, ByteBuffer buffer) {
        super("Signature");

        int length = buffer.getInt();
        assert length == 2;

        utfIndex = buffer.getShort();
    }

    @Override
    public void writeTo(DataOutputStream out, ClassDescriptor descriptor) throws IOException {
        descriptor.writeUtfIndex(out, utfIndex);
    }

    public static final AttributeFactory FACTORY = new AttributeFactory() {
        @Nullable
        @Override
        public Attribute read(AttributeType type, ClassDescriptor descriptor, String name, ByteBuffer buffer) {
            if (!name.equals("Signature")) return null;

            return new AttributeSignature(descriptor, buffer);
        }
    };
}
