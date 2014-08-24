package com.ess.jloader.packer.attributes;

import com.ess.jloader.packer.ClassDescriptor;
import com.ess.jloader.packer.PackUtils;
import com.ess.jloader.utils.BitOutputStream;
import com.google.common.collect.ComparisonChain;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class AttributeInnerClasses extends Attribute {

    private final Element[] elements;

    private final int anonymousClassCount;

    public AttributeInnerClasses(final AttrContext ctx, ByteBuffer buffer) {
        super("InnerClasses");

        int attrSize = buffer.getInt();
        int length = buffer.getShort() & 0xFFFF;
        assert attrSize == 2 + length * 4 * 2;

        elements = new Element[length];

        for (int i = 0; i < elements.length; i++) {
            elements[i] = new Element(ctx.getClassDescriptor(), buffer);
        }

//        Arrays.sort(elements, new Comparator<Element>() {
//            @Override
//            public int compare(Element o1, Element o2) {
//                int anInd1 = getAnonymousIndex(ctx.getClassDescriptor().getUtfByIndex(o1.innerClassIndex), ctx.getClassDescriptor().getClassName());
//                int anInd2 = getAnonymousIndex(ctx.getClassDescriptor().getUtfByIndex(o2.innerClassIndex), ctx.getClassDescriptor().getClassName());
//
//                return ComparisonChain.start()
//                        .compareTrueFirst(anInd1 != -1, anInd2 != -1)
//                        .compare(anInd1, anInd2)
//                        .compareTrueFirst()
//                        .result();
//            }
//        });

        anonymousClassCount = PackUtils.evaluateAnonymousClassCount(ctx.getClassDescriptor().getClassNode());
    }

//    private static int getAnonymousIndex(String anonymousClassName, String thisClassName) {
//        if (anonymousClassName.length() <= thisClassName.length()
//                || !anonymousClassName.startsWith(thisClassName)
//                || anonymousClassName.charAt(thisClassName.length()) != '$') {
//            return -1;
//        }
//
//        String sIndex = anonymousClassName.substring(thisClassName.length() + 1);
//        try {
//            return Integer.parseInt(sIndex);
//        } catch (NumberFormatException e) {
//            return -1;
//        }
//    }

    @Override
    public void writeTo(DataOutputStream out, BitOutputStream bitOut, ClassDescriptor descriptor) throws IOException {
        bitOut.writeSmall_0_3_8_16(anonymousClassCount);

        bitOut.writeLimitedShort(elements.length, descriptor.getClassesInterval().getCount());

        for (Element element : elements) {
            descriptor.getClassesInterval().writeIndexCompact(bitOut, element.innerClassIndex);
            descriptor.getClassesInterval().writeIndexCompactNullable(out, element.outerClassIndex);
            descriptor.getUtfInterval().writeIndexCompactNullable(bitOut, element.innerName);
            out.writeShort(element.access);
        }
    }

    private static class Element {
        private ClassDescriptor classDescriptor;

        private int innerClassIndex;
        private int outerClassIndex;
        private int innerName;
        private int access;

        public Element(ClassDescriptor classDescriptor, ByteBuffer buffer) {
            this.classDescriptor = classDescriptor;

            innerClassIndex = buffer.getShort() & 0xFFFF;
            outerClassIndex = buffer.getShort() & 0xFFFF;
            innerName = buffer.getShort() & 0xFFFF;
            access = buffer.getShort() & 0xFFFF;
        }

        @Override
        public String toString() {
            StringBuilder res = new StringBuilder();
            res.append(classDescriptor.getConstClasses().get(innerClassIndex - 1).getType());
            res.append(" ");

            if (outerClassIndex == 0) {
                res.append("null");
            }
            else {
                res.append(classDescriptor.getConstClasses().get(outerClassIndex - 1).getType());
            }
            res.append(" ");

            if (innerName == 0) {
                res.append("null");
            }
            else {
                res.append(classDescriptor.getUtfByIndex(innerName));
            }

            return res.toString();
        }
    }
}
