package com.ess.jloader.packer.attributes;

import com.ess.jloader.packer.ClassDescriptor;
import com.ess.jloader.packer.PackUtils;
import com.ess.jloader.utils.BitOutputStream;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class AttributeExceptions extends Attribute {

    private final List<Integer> exceptions = new ArrayList<Integer>();

    public AttributeExceptions(ByteBuffer buffer) {
        super("Exceptions");

        int length = buffer.getInt();

        int savedPosition = buffer.position();

        int exceptionCount = buffer.getShort() & 0xFFFF;

        for (int i = 0; i < exceptionCount; i++) {
            int exceptionType = buffer.getShort() & 0xFFFF;

            exceptions.add(exceptionType);
        }

        assert exceptions.size() > 0;
        assert buffer.position() == savedPosition + length;
    }

    @Override
    public void writeTo(DataOutputStream out, BitOutputStream bitOut, ClassDescriptor descriptor) throws IOException {
        Integer[] m = exceptions.toArray(new Integer[exceptions.size()]);
        Arrays.sort(m);

        for (Integer x : m) {
            PackUtils.writeLimitedNumber(out, x, descriptor.getConstClasses().size());
        }

        PackUtils.writeLimitedNumber(out, 0, descriptor.getConstClasses().size());
    }
}
