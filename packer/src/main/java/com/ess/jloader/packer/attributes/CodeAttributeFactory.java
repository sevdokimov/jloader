package com.ess.jloader.packer.attributes;

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
    public Attribute read(AttrContext ctx, String name, ByteBuffer buffer) {
        if (name.equals("LineNumberTable")) {
            return new AttributeLineNumberTable(ctx, buffer);
        }
        if (name.equals("LocalVariableTable")) {
            return new AttributeLocalVariable(ctx, buffer);
        }

        return new AttributeUnknown(name, buffer);
    }
}
