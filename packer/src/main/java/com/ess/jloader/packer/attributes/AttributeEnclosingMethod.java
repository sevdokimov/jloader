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
    private int classIndex;

    public AttributeEnclosingMethod(AttrContext ctx, ByteBuffer buffer) {
        super("EnclosingMethod");

        int size = buffer.getInt();
        assert size == 4;

        classIndex = buffer.getShort() & 0xFFFF;
        methodIndex = buffer.getShort() & 0xFFFF;
    }

    @Override
    public void writeTo(DataOutputStream defOut, BitOutputStream bitOut, ClassDescriptor descriptor) throws IOException {
        ConstClass enclosingClass = descriptor.getConstClasses().get(classIndex - 1);

        if (enclosingClass.getType().equals(Utils.generateEnclosingClassName(descriptor.getClassName()))) {
            bitOut.writeBit(true);
        }
        else {
            bitOut.writeBit(false);
            descriptor.getClassesInterval().writeIndexCompact(bitOut, classIndex);
        }

        descriptor.getNameAndTypeInterval().writeIndexCompactNullable(bitOut, methodIndex);
    }

}
