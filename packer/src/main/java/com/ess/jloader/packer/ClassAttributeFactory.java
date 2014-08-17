package com.ess.jloader.packer;

import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;

/**
 * @author Sergey Evdokimov
 */
public class ClassAttributeFactory extends AttributeFactory {

    public static final AttributeFactory INSTANCE = new ClassAttributeFactory();

    private ClassAttributeFactory() {

    }

    @Nullable
    @Override
    public Attribute read(ClassDescriptor descriptor, String name, ByteBuffer buffer) {
        if (name.equals("Signature")) {
            return new AttributeSignature(buffer);
        }
        if (name.equals("SourceFile")) {
            return new AttributeSourceFile(descriptor, buffer);
        }
        if (name.equals("InnerClasses")) {
            return new AttributeInnerClasses(descriptor, buffer);
        }

        return new UnknownAttribute(name, buffer);
    }
}
