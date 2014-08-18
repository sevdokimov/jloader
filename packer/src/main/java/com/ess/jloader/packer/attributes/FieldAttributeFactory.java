package com.ess.jloader.packer.attributes;

import com.ess.jloader.packer.ClassDescriptor;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;

/**
 * @author Sergey Evdokimov
 */
public class FieldAttributeFactory extends AttributeFactory {

    public static final AttributeFactory INSTANCE = new FieldAttributeFactory();

    private FieldAttributeFactory() {

    }

    @Nullable
    @Override
    public Attribute read(ClassDescriptor descriptor, String name, ByteBuffer buffer) {
        if (name.equals("Signature")) {
            return new AttributeSignature(buffer);
        }
        if (name.equals("ConstantValue")) {
            return new AttributeConstValue(buffer);
        }

        return new AttributeUnknown(name, buffer);
    }
}
