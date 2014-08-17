package com.ess.jloader.packer;

import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;

/**
 * @author Sergey Evdokimov
 */
public class CodeAttributeFactory extends AttributeFactory {

    public static final AttributeFactory INSTANCE = new CodeAttributeFactory();

    private CodeAttributeFactory() {

    }

    @Nullable
    @Override
    public Attribute read(ClassDescriptor descriptor, String name, ByteBuffer buffer) {
        return new UnknownAttribute(name, buffer);
    }
}
