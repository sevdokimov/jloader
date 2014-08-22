package com.ess.jloader.packer.attributes;

import com.ess.jloader.packer.ClassDescriptor;
import com.ess.jloader.packer.InvalidJarException;
import com.ess.jloader.packer.PackUtils;
import com.ess.jloader.utils.BitOutputStream;
import com.ess.jloader.utils.InsnTypes;
import com.ess.jloader.utils.Key;
import org.objectweb.asm.Opcodes;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class AttributeCode extends Attribute {

    public static final Key<AttributeCode> CODE_ATTR_KEY = Key.create("CODE_ATTR_KEY");

    private int maxStack;
    private int maxLocals;

    private byte[] code;

    private List<ExceptionRecord> exceptionTable;

    private final Collection<Attribute> attributes;

    public AttributeCode(AttrContext ctx, ByteBuffer buffer) {
        super("Code");

        int length = buffer.getInt();

        int savedPos = buffer.position();

        maxStack = buffer.getShort() & 0xFFFF;
        maxLocals = buffer.getShort() & 0xFFFF;

        int codeLength = buffer.getInt();

        code = PackUtils.readBytes(buffer, codeLength);


        int exceptionTableLength = buffer.getShort() & 0xFFFF;

        exceptionTable = new ArrayList<ExceptionRecord>(exceptionTableLength);
        for (int i = 0; i < exceptionTableLength; i++) {
            ExceptionRecord r = new ExceptionRecord();
            r.startPc = buffer.getShort() & 0xFFFF;
            r.endPc = buffer.getShort() & 0xFFFF;
            r.handlerPc = buffer.getShort() & 0xFFFF;
            r.catchType = buffer.getShort() & 0xFFFF;

            exceptionTable.add(r);
        }

        ctx.putProperty(CODE_ATTR_KEY, this);

        attributes = AttributeUtils.readAllAttributes(CodeAttributeFactory.INSTANCE, ctx, buffer);

        ctx.removeProperty(CODE_ATTR_KEY);

        assert buffer.position() - savedPos == length;
    }

    @Override
    public void writeTo(DataOutputStream out, BitOutputStream bitOut, ClassDescriptor descriptor) throws IOException {
        PackUtils.writeSmallShort3(out, maxStack);
        PackUtils.writeSmallShort3(out, maxLocals);

        writeCode(out, descriptor);

        PackUtils.writeSmallShort3(out, exceptionTable.size());

        for (ExceptionRecord record : exceptionTable) {
            record.writeTo(out);
        }

        List<Attribute> allAttributes = new ArrayList<Attribute>(attributes);
        List<Attribute> knownAttributes = new ArrayList<Attribute>();
        int attrInfo = AttributeUtils.extractKnownAttributes(allAttributes, knownAttributes, "LineNumberTable", "LocalVariableTable", "LocalVariableTypeTable", "StackMapTable");

        PackUtils.writeSmallShort3(out, attrInfo);

        for (Attribute attribute : knownAttributes) {
            attribute.writeTo(out, bitOut, descriptor);
        }

        for (Attribute attribute : allAttributes) {
            descriptor.getUtfInterval().writeIndexCompact(out, descriptor.getIndexByUtf(attribute.getName()));
            attribute.writeTo(out, bitOut, descriptor);
        }
    }

    private void skipPadding(ByteBuffer codeBuffer) {
        // skips 0 to 3 padding bytes
        while ((codeBuffer.position() & 3) > 0) {
            if (codeBuffer.get() != 0) {
                throw new RuntimeException();
            }
        }
    }

    private void writeCode(DataOutputStream out, ClassDescriptor descriptor) throws IOException {
        ByteBuffer codeBuffer = ByteBuffer.wrap(code);

        while (codeBuffer.hasRemaining()) {

            // visits the instruction at this offset
            int opcode = codeBuffer.get() & 0xFF;
            out.write(opcode);

            switch (InsnTypes.TYPE[opcode]) {
                case InsnTypes.NOARG_INSN:
                case InsnTypes.IMPLVAR_INSN:
                    break;

                case InsnTypes.VAR_INSN:
                case InsnTypes.SBYTE_INSN:
                case InsnTypes.LDC_INSN:
                    out.write(codeBuffer.get());
                    break;

                case InsnTypes.LABEL_INSN:
                case InsnTypes.SHORT_INSN:
                case InsnTypes.LDCW_INSN:
                case InsnTypes.TYPE_INSN:
                case InsnTypes.IINC_INSN:
                    PackUtils.write(out, codeBuffer, 2);
                    break;

                case InsnTypes.LABELW_INSN:
                    PackUtils.write(out, codeBuffer, 4);
                    break;

                case InsnTypes.WIDE_INSN:
                    opcode = codeBuffer.get() & 0xFF;
                    out.write(opcode);

                    if (opcode == Opcodes.IINC) {
                        PackUtils.write(out, codeBuffer, 4);
                    } else {
                        PackUtils.write(out, codeBuffer, 2);
                    }
                    break;

                case InsnTypes.TABL_INSN: {
                    skipPadding(codeBuffer);

                    int defaultLabel = codeBuffer.getInt(); // default ref
                    assert defaultLabel >= 0 && defaultLabel < code.length;
                    out.writeInt(defaultLabel);

                    int min = codeBuffer.getInt();
                    out.writeInt(min);

                    int max = codeBuffer.getInt();
                    assert min <= max;
                    out.writeInt(max);

                    for (int i = 0; i < max - min + 1; i++) {
                        int label = codeBuffer.getInt();
                        assert label >= 0 && label < code.length;
                        out.writeInt(label);
                    }
                    break;
                }

                case InsnTypes.LOOK_INSN: {
                    skipPadding(codeBuffer);

                    PackUtils.write(out, codeBuffer, 4);

                    int len = codeBuffer.getInt();
                    out.writeInt(len);

                    PackUtils.write(out, codeBuffer, 8 * len);
                    break;
                }

                case InsnTypes.ITFMETH_INSN:
                case InsnTypes.FIELDORMETH_INSN:
                    int ref = codeBuffer.getShort() & 0xFFFF;

                    if (opcode == Opcodes.GETFIELD || opcode == Opcodes.PUTFIELD
                            || opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC) {
                        out.writeShort(ref - descriptor.getFieldInterval().getFirstIndex());
                    } else if (opcode == Opcodes.INVOKEINTERFACE) {
                        out.writeShort(ref - descriptor.getImethodInterval().getFirstIndex());

                        out.write(codeBuffer.get());
                        if (codeBuffer.get() != 0) throw new InvalidJarException();
                    }
                    else if (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKESPECIAL
                            || opcode == Opcodes.INVOKESTATIC) {
                        out.writeShort(ref - descriptor.getMethodInterval().getFirstIndex());
                    }
                    else {
                        throw new UnsupportedOperationException(String.valueOf(opcode));
                    }
                    break;

//                case InsnTypes.INDYMETH_INSN: {
//                    throw new UnsupportedOperationException(); // todo Implement support of INVOKEDYNAMIC!!!
//                }

                case InsnTypes.MANA_INSN:
                    PackUtils.write(out, codeBuffer, 3);
                    break;

                default:
                    throw new UnsupportedOperationException(String.valueOf(opcode));
            }
        }

        out.write(0xFF); // end of code marker.
    }


    public int getMaxStack() {
        return maxStack;
    }

    public int getMaxLocals() {
        return maxLocals;
    }

    public byte[] getCode() {
        return code;
    }

    public List<ExceptionRecord> getExceptionTable() {
        return exceptionTable;
    }

    public Collection<Attribute> getAttributes() {
        return attributes;
    }

    public static class ExceptionRecord {
        public int startPc;
        public int endPc;
        public int handlerPc;
        public int catchType;

        public void writeTo(DataOutputStream out) throws IOException {
            out.writeShort(startPc);
            out.writeShort(endPc);
            out.writeShort(handlerPc);
            out.writeShort(catchType);
        }
    }
}
