package com.ess.jloader.packer.attributes;

import com.ess.jloader.packer.ClassDescriptor;
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
    public Attribute read(AttrContext ctx, String name, ByteBuffer buffer) {
        if (name.equals("Signature")) {
            return new AttributeSignature(buffer);
        }
        if (name.equals("SourceFile")) {
            return new AttributeSourceFile(ctx, buffer);
        }
        if (name.equals("InnerClasses")) {
            return new AttributeInnerClasses(ctx, buffer);
        }
        if (name.equals("EnclosingMethod")) {
            return new AttributeEnclosingMethod(ctx, buffer);
        }

        return new AttributeUnknown(name, buffer);
    }
}
