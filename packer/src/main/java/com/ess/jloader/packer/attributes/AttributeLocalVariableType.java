package com.ess.jloader.packer.attributes;

import com.ess.jloader.packer.ClassDescriptor;
import com.ess.jloader.packer.InvalidJarException;
import com.ess.jloader.utils.BitOutputStream;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author Sergey Evdokimov
 */
public class AttributeLocalVariableType extends Attribute {

    private final LocalVarElement[] elements;

    private final AttributeCode codeAttr;

    public AttributeLocalVariableType(AttrContext ctx, ByteBuffer buffer) {
        super("LocalVariableTypeTable");

        codeAttr = ctx.getProperty(AttributeCode.CODE_ATTR_KEY);

        int attrSize = buffer.getInt();

        int length = buffer.getShort() & 0xFFFF;
        assert attrSize == 2 + length * 5*2;

        if (length == 0) throw new InvalidJarException();

        elements = new LocalVarElement[length];

        for (int i = 0; i < length; i++) {
            elements[i] = new LocalVarElement(ctx.getClassDescriptor(), buffer);
        }
    }

    @Override
    public void writeTo(DataOutputStream defOut, BitOutputStream bitOut, ClassDescriptor descriptor) throws IOException {
        AttributeLocalVariable localVarTable = (AttributeLocalVariable) AttributeUtils.findAttributeByName(
                codeAttr.getAttributes(), "LocalVariableTable");
        assert localVarTable != null;

        LocalVarElement[] localVars = localVarTable.getElements();

        for (int i = 0; i < localVars.length; i++) {
            LocalVarElement e = localVars[i];
            int correspondingElementIndex = -1;

            for (int j = 0; j < elements.length; j++) {
                if (elements[j].codePos == e.codePos
                        && elements[j].len == e.len
                        && elements[j].index == e.index
                        && elements[j].nameIndex == e.nameIndex
                        ) {
                    if (correspondingElementIndex == -1) {
                        correspondingElementIndex = j;
                    } else {
                        throw new InvalidJarException();
                    }
                }
            }

            if (correspondingElementIndex != -1) {
                bitOut.writeBit(true);
                descriptor.getUtfInterval().writeIndexCompact(defOut, elements[correspondingElementIndex].descriptorIndex);
            }
            else {
                bitOut.writeBit(false);
            }
        }
    }
}
