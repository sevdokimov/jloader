package com.ess.jloader.packer.attributes;

import com.ess.jloader.packer.ClassDescriptor;
import com.google.common.collect.ComparisonChain;

import java.nio.ByteBuffer;

/**
* @author Sergey Evdokimov
*/
class LocalVarElement implements Comparable<LocalVarElement> {
    private final ClassDescriptor descriptor;

    public int codePos;
    public int len;
    public int nameIndex;
    public int descriptorIndex;
    public int index;

    public LocalVarElement(ClassDescriptor descriptor, ByteBuffer buffer) {
        this(descriptor,
                buffer.getShort() & 0xFFFF, buffer.getShort() & 0xFFFF, buffer.getShort() & 0xFFFF,
                buffer.getShort() & 0xFFFF, buffer.getShort() & 0xFFFF);
    }

    public LocalVarElement(ClassDescriptor descriptor, int codePos, int len, int nameIndex, int descriptorIndex, int index) {
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
    public int compareTo(LocalVarElement o) {
        return ComparisonChain.start()
                .compare(codePos, o.codePos)
                .compare(o.len, len)
                .compare(index, o.index)
                .result();
    }
}
