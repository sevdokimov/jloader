package com.ess.jloader.packer;

import org.jetbrains.annotations.Nullable;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * @author Sergey Evdokimov
 */
public class AttributeExceptions extends Attribute {

    private final List<Integer> exceptions = new ArrayList<Integer>();

    public AttributeExceptions(ClassDescriptor descriptor, ByteBuffer buffer) {
        super("Exceptions");

        int length = buffer.getInt();

        int savedPosition = buffer.position();

        int exceptionCount = buffer.getShort() & 0xFFFF;

        for (int i = 0; i < exceptionCount; i++) {
            int exceptionType = buffer.getShort() & 0xFFFF;
            assert exceptionType <= descriptor.getConstClasses().size();

            exceptions.add(exceptionType);
        }

        assert exceptions.size() > 0;
        assert buffer.position() == savedPosition + length;
    }

    @Override
    public void writeTo(DataOutputStream out, ClassDescriptor descriptor) throws IOException {
        Integer[] m = exceptions.toArray(new Integer[exceptions.size()]);
        Arrays.sort(m);

        for (Integer x : m) {
            PackUtils.writeLimitedNumber(out, x, descriptor.getConstClasses().size());
        }

        PackUtils.writeLimitedNumber(out, 0, descriptor.getConstClasses().size());
    }

    public static final AttributeFactory FACTORY = new AttributeFactory() {
        @Nullable
        @Override
        public Attribute read(AttributeType type, ClassDescriptor descriptor, String name, ByteBuffer buffer) {
            if (type != AttributeType.METHOD || !name.equals("Exceptions")) return null;

            return new AttributeExceptions(descriptor, buffer);
        }
    };
}
