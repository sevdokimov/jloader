package com.ess.jloader.packer.attributes;

import com.ess.jloader.packer.ClassDescriptor;
import com.ess.jloader.packer.PackUtils;
import com.ess.jloader.utils.BitOutputStream;

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

    public AttributeInnerClasses(AttrContext ctx, ByteBuffer buffer) {
        super("InnerClasses");

        length = buffer.getInt();
        body = PackUtils.readBytes(buffer, length);

        anonymousClassCount = PackUtils.evaluateAnonymousClassCount(ctx.getClassDescriptor().getClassNode());
    }


    @Override
    public void writeTo(DataOutputStream out, BitOutputStream bitOut, ClassDescriptor descriptor) throws IOException {
        bitOut.writeSmall_0_3_8_16(anonymousClassCount);

        out.writeInt(length);
        out.write(body);
    }
}
