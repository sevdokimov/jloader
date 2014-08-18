package com.ess.jloader.packer.attributes;

import com.ess.jloader.packer.ClassDescriptor;
import com.ess.jloader.utils.BitOutputStream;
import com.ess.jloader.utils.Utils;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author Sergey Evdokimov
 */
public class AttributeSourceFile extends Attribute {

    private final String sourceFile;
    private final boolean isStandard;

    public AttributeSourceFile(AttrContext ctx, ByteBuffer buffer) {
        super("SourceFile");

        int length = buffer.getInt();
        assert length == 2;

        int utfIndex = buffer.getShort() & 0xFFFF;
        sourceFile = ctx.getClassDescriptor().getUtfByIndex(utfIndex);

        isStandard = sourceFile.equals(Utils.generateSourceFileName(ctx.getClassDescriptor().getClassName()));
    }

    @Override
    public void writeTo(DataOutputStream out, BitOutputStream bitOut, ClassDescriptor descriptor) throws IOException {
        bitOut.writeBit(isStandard);
        if (!isStandard) {
            descriptor.getUtfInterval().writeIndexCompact(bitOut, descriptor.getIndexByUtf(sourceFile));
        }
    }
}
