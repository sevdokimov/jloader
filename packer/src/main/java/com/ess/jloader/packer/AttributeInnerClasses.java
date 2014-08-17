package com.ess.jloader.packer;

import com.ess.jloader.utils.BitOutputStream;
import org.jetbrains.annotations.Nullable;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author Sergey Evdokimov
 */
public class AttributeInnerClasses extends Attribute {

    private final int length;

    private final byte[] body;

    private final int anonymousClassCount;

    public AttributeInnerClasses(ClassDescriptor descriptor, String name, ByteBuffer buffer) {
        super(name);

        length = buffer.getInt();
        body = PackUtils.readBytes(buffer, length);

        anonymousClassCount = PackUtils.evaluateAnonymousClassCount(descriptor.getClassNode());
    }


    @Override
    public void writeTo(DataOutputStream out, BitOutputStream bitOut, ClassDescriptor descriptor) throws IOException {
        bitOut.writeSmall_0_3_8_16(anonymousClassCount);

        out.writeInt(length);
        out.write(body);
    }

    public static final AttributeFactory<AttributeInnerClasses> FACTORY = new AttributeFactory<AttributeInnerClasses>() {
        @Nullable
        @Override
        public AttributeInnerClasses read(ClassDescriptor descriptor, String name, ByteBuffer buffer) {
            if (!name.equals("InnerClasses")) return null;
            return new AttributeInnerClasses(descriptor, name, buffer);
        }
    };
}
