package com.ess.jloader.packer.attributes;

import com.ess.jloader.packer.ClassDescriptor;
import com.ess.jloader.packer.consts.ConstClass;
import com.ess.jloader.utils.BitOutputStream;
import com.ess.jloader.utils.Utils;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author Sergey Evdokimov
 */
public class AttributeEnclosingMethod extends Attribute {

    private int methodIndex;

    public AttributeEnclosingMethod(AttrContext ctx, ByteBuffer buffer) {
        super("EnclosingMethod");

        int size = buffer.getInt();
        assert size == 4;

        int classIndex = buffer.getShort() & 0xFFFF;

        ConstClass enclosingClass = ctx.getClassDescriptor().getConstClasses().get(classIndex - 1);
        assert enclosingClass.getType().equals(Utils.generateEnclosingClassName(ctx.getClassDescriptor().getClassName()));

        methodIndex = buffer.getShort() & 0xFFFF;
    }

    @Override
    public void writeTo(DataOutputStream defOut, BitOutputStream bitOut, ClassDescriptor descriptor) throws IOException {
        bitOut.writeShort(methodIndex);
    }

}
