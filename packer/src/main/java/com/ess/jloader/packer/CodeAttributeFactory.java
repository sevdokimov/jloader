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
        if (name.equals("LineNumberTable")) {
            return new AttributeLineNumberTable(descriptor, buffer);
        }

        return new UnknownAttribute(name, buffer);
    }
}
