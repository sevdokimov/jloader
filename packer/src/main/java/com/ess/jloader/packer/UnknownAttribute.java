package com.ess.jloader.packer;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * @author Sergey Evdokimov
 */
public class UnknownAttribute extends Attribute {

    private int length;

    private byte[] body;

    public UnknownAttribute(String name, ByteBuffer buffer) throws IOException {
        super(name);

        length = buffer.getInt();

        int newPosition = buffer.position() + length;

        body = Arrays.copyOfRange(buffer.array(), buffer.position(), newPosition);

        buffer.position(newPosition);
    }


    @Override
    public void writeTo(DataOutputStream out, ClassDescriptor descriptor) throws IOException {
        out.writeInt(length);
        out.write(body);
    }

    public static final AttributeFactory<UnknownAttribute> FACTORY = new AttributeFactory<UnknownAttribute>() {
        @Override
        public UnknownAttribute read(AttributeType type, String name, ByteBuffer buffer) throws IOException {
            return new UnknownAttribute(name, buffer);
        }
    };
}
