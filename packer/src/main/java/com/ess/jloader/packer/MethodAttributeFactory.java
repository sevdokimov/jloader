package com.ess.jloader.packer;

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
    public Attribute read(ClassDescriptor descriptor, String name, ByteBuffer buffer) {
        if (name.equals("Signature")) {
            return new AttributeSignature(buffer);
        }
        if (name.equals("Exceptions")) {
            return new AttributeExceptions(buffer);
        }
        if (name.equals("Code")) {
            return new AttributeCode(descriptor, buffer);
        }

        return new UnknownAttribute(name, buffer);
    }
}
