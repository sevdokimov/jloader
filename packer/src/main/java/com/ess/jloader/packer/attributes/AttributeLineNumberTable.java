package com.ess.jloader.packer.attributes;

import com.ess.jloader.packer.ClassDescriptor;
import com.ess.jloader.packer.InvalidJarException;
import com.ess.jloader.utils.BitOutputStream;
import com.ess.jloader.utils.Key;
import org.objectweb.asm.*;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Sergey Evdokimov
 */
public class AttributeLineNumberTable extends Attribute {

    private static final Key<Integer> LINE_NUMBER_BIT_COUNT_KEY = Key.create("LINE_NUMBER_BIT_COUNT_KEY");

    private final Element[] elements;

    private AttributeCode codeAttr;

    public AttributeLineNumberTable(AttrContext ctx, ByteBuffer buffer) {
        super("LineNumberTable");

        codeAttr = ctx.getProperty(AttributeCode.CODE_ATTR_KEY);

        int attrSize = buffer.getInt();

        int length = buffer.getShort() & 0xFFFF;
        elements = new Element[length];

        if (length == 0) throw new InvalidJarException();

        for (int i = 0; i < length; i++) {
            elements[i] = new Element(buffer.getShort() & 0xFFFF, buffer.getShort() & 0xFFFF);

            if (elements[i].codePos >= codeAttr.getCode().length) throw new InvalidJarException();
        }

        for (int i = 1; i < elements.length; i++) {
            if (elements[i - 1].codePos >= elements[i].codePos) throw new InvalidJarException();
        }

        assert attrSize == 2 + length * 4;
    }

    @Override
    public void writeTo(DataOutputStream defOut, BitOutputStream bitOut, ClassDescriptor descriptor) throws IOException {
        Integer bitCount = descriptor.getProperty(LINE_NUMBER_BIT_COUNT_KEY);
        if (bitCount == null) {
            bitCount = evaluateLineNumbersBitCount(descriptor.getClassReader());
            descriptor.putProperty(LINE_NUMBER_BIT_COUNT_KEY, bitCount);

            bitOut.writeBits(bitCount - 1, 4);
            assert bitCount - 1 < 16;
        }

        bitOut.writeBits(elements[0].line, bitCount);
        if (elements[0].codePos != 0) {
            bitOut.writeBit(true);
            bitOut.writeLimitedShort(elements[0].codePos, codeAttr.getCode().length - 1);
        } else {
            bitOut.writeBit(false);
        }

        if (elements.length == 1) {
            bitOut.writeBit(true);
        }
        else {
            bitOut.writeBit(false);

            for (int i = 1; i < elements.length; i++) {
                int d = elements[i].line - elements[i - 1].line;

                if (d < -127 || d > 126) {
                    defOut.write(-128);
                    bitOut.writeBits(elements[i].line, bitCount);
                }
                else {
                    defOut.write(d);
                }
            }
            defOut.write(127); // 127 - end of the sequence

            for (int i = 1; i < elements.length; i++) {
                int x = elements[i].codePos - elements[i - 1].codePos - 1;

                if (x < 29) { // 0 - 28
                    bitOut.writeBits(x, 5);
                }
                else if (x < 45) { // 29 - 44  (16)
                    bitOut.writeBits(29, 5);
                    bitOut.writeBits(x - 29, 4);
                }
                else if (x < 173) { // 45 - 172  (128)
                    bitOut.writeBits(30, 5);
                    bitOut.writeBits(x - 45, 7);
                }
                else {
                    bitOut.writeBits(31, 5);
                    bitOut.writeShort(x);
                }
            }
        }
    }

    public static int evaluateLineNumbersBitCount(ClassReader classReader) {
        final AtomicInteger maxLineNumber = new AtomicInteger(1); // avoid return 0

        classReader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitLineNumber(int line, Label start) {
                        if (line > maxLineNumber.get()) {
                            maxLineNumber.set(line);
                        }
                    }
                };
            }
        }, 0);

        assert maxLineNumber.get() < 0x10000;

        return 32 - Integer.numberOfLeadingZeros(maxLineNumber.get());
    }

    private static class Element {
        public int codePos;
        public int line;

        public Element(int codePos, int line) {
            this.codePos = codePos;
            this.line = line;
        }

        @Override
        public String toString() {
            return codePos + " " + line;
        }

//        @Override
//        public int compareTo(Element o) {
//            return codePos - o.codePos;
//        }
    }

}
