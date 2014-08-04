package com.ess.jloader.packer;

import org.jetbrains.annotations.Nullable;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author Sergey Evdokimov
 */
public class UnknownAttribute extends Attribute {

    private int length;

    private byte[] body;

    public UnknownAttribute(String name, ByteBuffer buffer) {
        super(name);

        length = buffer.getInt();
        body = PackUtils.readBytes(buffer, length);
    }


    @Override
    public void writeTo(DataOutputStream out, ClassDescriptor descriptor) throws IOException {
        out.writeInt(length);
        out.write(body);
    }

    public static final AttributeFactory<UnknownAttribute> FACTORY = new AttributeFactory<UnknownAttribute>() {
        @Nullable
        @Override
        public UnknownAttribute read(AttributeType type, ClassDescriptor descriptor, String name, ByteBuffer buffer) {
            return new UnknownAttribute(name, buffer);
        }
    };
}
