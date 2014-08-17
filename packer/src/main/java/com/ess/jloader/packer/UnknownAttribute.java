package com.ess.jloader.packer;

import com.ess.jloader.utils.BitOutputStream;
import org.jetbrains.annotations.Nullable;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author Sergey Evdokimov
 */
public class UnknownAttribute extends Attribute {

    private final int length;

    private final byte[] body;

    public UnknownAttribute(String name, ByteBuffer buffer) {
        super(name);

        length = buffer.getInt();
        body = PackUtils.readBytes(buffer, length);
    }


    @Override
    public void writeTo(DataOutputStream out, BitOutputStream bitOut, ClassDescriptor descriptor) throws IOException {
        out.writeInt(length);
        out.write(body);
    }

    public static final AttributeFactory<UnknownAttribute> FACTORY = new AttributeFactory<UnknownAttribute>() {
        @Nullable
        @Override
        public UnknownAttribute read(ClassDescriptor descriptor, String name, ByteBuffer buffer) {
            return new UnknownAttribute(name, buffer);
        }
    };
}
