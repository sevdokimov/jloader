package com.ess.jloader.packer.attributes;

import com.ess.jloader.packer.ClassDescriptor;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;

/**
 * @author Sergey Evdokimov
 */
public class MethodAttributeFactory extends AttributeFactory {

    public static final AttributeFactory INSTANCE = new MethodAttributeFactory();

    private MethodAttributeFactory() {

    }

    @Nullable
    @Override
    public Attribute read(AttrContext ctx, String name, ByteBuffer buffer) {
        if (name.equals("Signature")) {
            return new AttributeSignature(buffer);
        }
        if (name.equals("Exceptions")) {
            return new AttributeExceptions(buffer);
        }
        if (name.equals("Code")) {
            return new AttributeCode(ctx, buffer);
        }

        return new AttributeUnknown(name, buffer);
    }
}
