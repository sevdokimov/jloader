package com.ess.jloader.packer.attributes;

import com.ess.jloader.packer.ClassDescriptor;
import com.ess.jloader.packer.ConstIndexInterval;
import com.ess.jloader.packer.InvalidJarException;
import com.ess.jloader.packer.PackUtils;
import com.ess.jloader.utils.BitOutputStream;
import com.ess.jloader.utils.Utils;
import com.google.common.collect.ComparisonChain;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;

/**
 * @author Sergey Evdokimov
 */
public class AttributeLocalVariable extends Attribute {

    private final Element[] elements;

    private AttributeCode codeAttr;

    public AttributeLocalVariable(AttrContext ctx, ByteBuffer buffer) {
        super("LocalVariableTable");

        codeAttr = ctx.getProperty(AttributeCode.CODE_ATTR_KEY);

        int attrSize = buffer.getInt();

        int length = buffer.getShort() & 0xFFFF;
        assert attrSize == 2 + length * 5*2;

        if (length == 0) throw new InvalidJarException();

        elements = new Element[length];

        for (int i = 0; i < length; i++) {
            elements[i] = new Element(
                    ctx.getClassDescriptor(),
                    buffer.getShort() & 0xFFFF,
                    buffer.getShort() & 0xFFFF,
                    buffer.getShort() & 0xFFFF,
                    buffer.getShort() & 0xFFFF,
                    buffer.getShort() & 0xFFFF);
        }

        Arrays.sort(elements);
        for (int i = 0; i < elements.length; i++) {
            if (elements[i].codePos > 0
                   // || elements[i].len != codeAttr.getCode().length
                    ) break;

            if ("this".equals(ctx.getClassDescriptor().getUtfByIndex(elements[i].nameIndex))) {
                if (i > 0) {
                    Collections.swap(Arrays.asList(elements), 0, i);
                }
                break;
            }
        }
    }

    @Override
    public void writeTo(DataOutputStream defOut, BitOutputStream bitOut, ClassDescriptor descriptor) throws IOException {
        int codeLen = codeAttr.getCode().length;

        int hasThis = 0;
        int paramCount = 0;
        int plainVarCount = 0;

        int i = 0;
        if ("this".equals(descriptor.getUtfByIndex(elements[0].nameIndex))
                && elements[0].codePos == 0
                && elements[0].index == 0
                && ("L" + descriptor.getClassName() + ';').equals(descriptor.getUtfByIndex(elements[0].descriptorIndex))) {
            if (elements[0].len != codeLen) {
                throw new InvalidJarException(); // !!!
            }

            i = 1;
            hasThis = 1;
        }

        while (i < elements.length
                && elements[i].index == i
                && elements[i].codePos == 0 && elements[i].len == codeAttr.getCode().length
                && paramCount < 7) {
            i++;
            paramCount++;
        }

        while (i < elements.length
                && elements[i].index == i
                && elements[i].getEnd() == codeAttr.getCode().length
                && plainVarCount < 15) {
            i++;
            plainVarCount++;
        }

        defOut.write(hasThis | (paramCount << 1) | (plainVarCount << 4));

        ConstIndexInterval utfInterval = descriptor.getUtfInterval();

        for (int j = hasThis; j < elements.length; j++) {
            Element e = elements[j];

            utfInterval.writeIndexCompact(bitOut, e.nameIndex);
            PackUtils.writeLimitedNumber(defOut, e.descriptorIndex + 1 - utfInterval.getFirstIndex(), utfInterval.getCount());
        }
        PackUtils.writeLimitedNumber(defOut, 0, utfInterval.getCount());

        for (int j = 0; j < plainVarCount; j++) {
            bitOut.writeLimitedShort(elements[hasThis + paramCount + j].codePos, codeLen);
        }

        for (int j = i; j < elements.length; j++) {
            Element e = elements[j];

            bitOut.writeLimitedShort(e.codePos, codeLen);
            assert e.getEnd() <= codeLen;
            PackUtils.writeLimitedNumber(defOut, e.getEnd(), codeLen);
            bitOut.writeLimitedShort(e.index, codeAttr.getMaxLocals());
        }

        if (Utils.CHECK_LIMITS) {
            defOut.write(125);
            bitOut.write(125);
        }
    }

    private static class Element implements Comparable<Element> {
        private final ClassDescriptor descriptor;

        public int codePos;
        public int len;
        public int nameIndex;
        public int descriptorIndex;
        public int index;

        public Element(ClassDescriptor descriptor, int codePos, int len, int nameIndex, int descriptorIndex, int index) {
            this.descriptor = descriptor;

            this.codePos = codePos;
            this.len = len;
            this.nameIndex = nameIndex;
            this.descriptorIndex = descriptorIndex;
            this.index = index;
        }

        @Override
        public String toString() {
            return codePos + "-" + getEnd() + " i" + index + "  " + descriptor.getUtfByIndex(nameIndex) + "  " + descriptor.getUtfByIndex(descriptorIndex);
        }

        public int getEnd() {
            return codePos + len;
        }

        @Override
        public int compareTo(Element o) {
            return ComparisonChain.start()
                    .compare(codePos, o.codePos)
                    .compare(o.len, len)
                    .compare(index, o.index)
                    .result();
        }
    }

}
