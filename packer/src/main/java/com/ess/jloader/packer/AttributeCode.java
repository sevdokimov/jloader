package com.ess.jloader.packer;

import com.ess.jloader.utils.InsnTypes;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class AttributeCode extends Attribute {

    private int maxStack;
    private int maxLocals;

    private byte[] code;

    private List<ExceptionRecord> exceptionTable;

    private ArrayList<Attribute> attributes;

    public AttributeCode(ByteBuffer buffer, ClassDescriptor descriptor) {
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

        attributes = AttributeUtils.readAllAttributes(AttributeType.CODE, descriptor, buffer);

        assert buffer.position() - savedPos == length;

        patchCode(descriptor);
    }

    @Override
    public void writeTo(DataOutputStream out, ClassDescriptor descriptor) throws IOException {
        out.writeShort(maxStack);
        out.writeShort(maxLocals);

        out.writeInt(code.length);
        out.write(code);

        out.writeShort(exceptionTable.size());
        for (ExceptionRecord record : exceptionTable) {
            record.writeTo(out);
        }

        PackUtils.writeSmallShort3(out, attributes.size());
        for (Attribute attribute : attributes) {
            descriptor.writeUtfIndex(out, descriptor.getIndexByUtf(attribute.getName()));
            assert attribute instanceof UnknownAttribute;
            attribute.writeTo(out, descriptor);
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

    private void patchCode(ClassDescriptor descriptor) {
        ByteBuffer codeBuffer = ByteBuffer.wrap(code);

        while (codeBuffer.hasRemaining()) {

            // visits the instruction at this offset
            int opcode = codeBuffer.get() & 0xFF;

            switch (InsnTypes.TYPE[opcode]) {
                case InsnTypes.NOARG_INSN:
                case InsnTypes.IMPLVAR_INSN:
                    break;

                case InsnTypes.VAR_INSN:
                case InsnTypes.SBYTE_INSN:
                case InsnTypes.LDC_INSN:
                    codeBuffer.get();
                    break;

                case InsnTypes.LABEL_INSN:
                case InsnTypes.SHORT_INSN:
                case InsnTypes.LDCW_INSN:
                case InsnTypes.TYPE_INSN:
                case InsnTypes.IINC_INSN:
                    codeBuffer.getShort();
                    break;

                case InsnTypes.LABELW_INSN:
                    codeBuffer.getInt();
                    break;

                case InsnTypes.WIDE_INSN:
                    opcode = codeBuffer.get() & 0xFF;
                    if (opcode == Opcodes.IINC) {
                        codeBuffer.getInt();
                    } else {
                        codeBuffer.getShort();
                    }
                    break;

                case InsnTypes.TABL_INSN: {
                    skipPadding(codeBuffer);

                    int defaultLabel = codeBuffer.getInt(); // default ref
                    assert defaultLabel >= 0 && defaultLabel < code.length;

                    int min = codeBuffer.getInt();
                    int max = codeBuffer.getInt();
                    assert min <= max;

                    for (int i = 0; i < max - min + 1; i++) {
                        int label = codeBuffer.getInt();
                        assert label >= 0 && label < code.length;
                    }
                    break;
                }

                case InsnTypes.LOOK_INSN: {
                    skipPadding(codeBuffer);

                    codeBuffer.getInt();
                    int len = codeBuffer.getInt();
                    codeBuffer.position(codeBuffer.position() + 8 * len);
                    break;
                }

                case InsnTypes.ITFMETH_INSN:
                case InsnTypes.FIELDORMETH_INSN:
                    if (opcode == Opcodes.GETFIELD || opcode == Opcodes.PUTFIELD
                            || opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC) {
                        int fieldIndex = descriptor.formatFiledIndex(codeBuffer.getShort() & 0xFFFF);
                        codeBuffer.putShort(codeBuffer.position() - 2, (short) fieldIndex);
                    } else if (opcode == Opcodes.INVOKEINTERFACE) {
                        int imethIndex = descriptor.formatIMethodIndex(codeBuffer.getShort() & 0xFFFF);
                        codeBuffer.putShort(codeBuffer.position() - 2, (short) imethIndex);

                        codeBuffer.getShort();
                    }
                    else if (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKESPECIAL
                            || opcode == Opcodes.INVOKESTATIC) {
                        int methIndex = descriptor.formatMethodIndex(codeBuffer.getShort() & 0xFFFF);
                        codeBuffer.putShort(codeBuffer.position() - 2, (short) methIndex);
                    }
                    else {
                        throw new UnsupportedOperationException(String.valueOf(opcode));
                    }
                    break;

//                case InsnTypes.INDYMETH_INSN: {
//                    throw new UnsupportedOperationException(); // todo Implement support of INVOKEDYNAMIC!!!
//                }

                case InsnTypes.MANA_INSN:
                    codeBuffer.getShort();
                    codeBuffer.get();
                    break;

                default:
                    throw new UnsupportedOperationException(String.valueOf(opcode));
            }
        }
    }

    public static final AttributeFactory FACTORY = new AttributeFactory() {
        @Nullable
        @Override
        public Attribute read(AttributeType type, ClassDescriptor descriptor, String name, ByteBuffer buffer) {
            if (type != AttributeType.METHOD || !name.equals("Code")) return null;

            return new AttributeCode(buffer, descriptor);
        }
    };

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
