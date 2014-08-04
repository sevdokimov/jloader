package com.ess.jloader.packer;

import com.ess.jloader.utils.Utils;
import org.jetbrains.annotations.Nullable;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
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

    private int length;

    public AttributeCode(ByteBuffer buffer, ClassDescriptor descriptor) {
        super("Code");

        length = buffer.getInt();

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

        descriptor.writeSmallShort3(out, attributes.size());
        for (Attribute attribute : attributes) {
            descriptor.writeUtfIndex(out, descriptor.getIndexByUtf(attribute.getName()));
            assert attribute instanceof UnknownAttribute;
            attribute.writeTo(out, descriptor);
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
