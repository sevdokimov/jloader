package com.ess.jloader.packer;

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

    public AttributeSourceFile(ClassDescriptor descriptor, ByteBuffer buffer) {
        super("SourceFile");

        int length = buffer.getInt();
        assert length == 2;

        int utfIndex = buffer.getShort() & 0xFFFF;
        sourceFile = descriptor.getUtfByIndex(utfIndex);

        isStandard = sourceFile.equals(Utils.generateSourceFileName(descriptor.getClassName()));
    }

    @Override
    public void writeTo(DataOutputStream out, BitOutputStream bitOut, ClassDescriptor descriptor) throws IOException {
        bitOut.writeBit(isStandard);
        if (!isStandard) {
            descriptor.getUtfInterval().writeIndexCompact(bitOut, descriptor.getIndexByUtf(sourceFile));
        }
    }
}
