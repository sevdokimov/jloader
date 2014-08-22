package com.ess.jloader.utils;

import com.ess.jloader.packer.PackUtils;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassReader;

import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Sergey Evdokimov
 */
public class ClassComparator {

    private byte[] sourceClass;
    private byte[] targetClass;

    private ByteBuffer sourceBuffer;
    private ByteBuffer targetBuffer;

    private ClassReader cr;
    private ClassReader sourceClassReader;
    private ClassReader targetClassReader;

    private char[] charBuffer;

    private ClassComparator(byte[] sourceClass, byte[] targetClass) {
        this.sourceClass = sourceClass;
        this.targetClass = targetClass;
    }

    private void compare() {
        sourceClassReader = new ClassReader(sourceClass);
        targetClassReader = new ClassReader(targetClass);

        sourceBuffer = ByteBuffer.wrap(sourceClass);
        targetBuffer = ByteBuffer.wrap(targetClass);

        assert sourceClassReader.header == targetClassReader.header;
        processBytes(sourceClassReader.header);

        cr = sourceClassReader;
        charBuffer = new char[cr.getMaxStringLength()];

        processShort(); // access flags
        processShort(); // this class index
        processShort(); // super class index

        int interfaceCount = processShort();
        processBytes(interfaceCount * 2);

        int fieldCount = processShort();
        for (int i = 0; i < fieldCount; i++) {
            processShort(); // access flags

            String name = processUtf();
            String descr = processUtf();

            processAttributes(false);
        }

        int methodCount = processShort();
        for (int i = 0; i < methodCount; i++) {
            int accessFlags = processShort(); // access flags

            String name = processUtf();
            String descr = processUtf();

            processAttributes(!Modifier.isNative(accessFlags) && !Modifier.isAbstract(accessFlags));
        }

        processAttributes(false);

        assert !sourceBuffer.hasRemaining();
        assert !targetBuffer.hasRemaining();
    }

    private void processCodeAttribute() {
        String codeAttrName = processUtf();
        assert codeAttrName.equals("Code");

        int attrSize = processInt();
        int savedPos = sourceBuffer.position();

        processShort(); // max stack
        processShort(); // max locals

        int codeLen = processInt();
        processBytes(codeLen);

        int exceptionTableLen = processShort();
        processBytes(exceptionTableLen * 4 * 2);

        processAttributes(false);

        assert savedPos + attrSize == sourceBuffer.position();
    }

    private void processAttributes(boolean hasCodeAttribute) {
        int attrCount = processShort();

        if (hasCodeAttribute) {
            attrCount--;

            processCodeAttribute();
        }

        Set<Attr> sourceAttr = new LinkedHashSet<Attr>();
        Set<Attr> targetAttr = new LinkedHashSet<Attr>();

        for (int i = 0; i < attrCount; i++) {
            sourceAttr.add(readAttr(sourceBuffer, sourceClassReader));
            targetAttr.add(readAttr(targetBuffer, targetClassReader));
        }

        for (Attr attr : sourceAttr) {
            assert targetAttr.contains(attr) : attr.name;
        }
    }

    private Attr readAttr(ByteBuffer buffer, ClassReader cr) {
        String attrName = cr.readUTF8(buffer.position(), charBuffer);
        buffer.getShort();

        if (attrName.equals("Exceptions")) {
            return new ExceptionsAttr(buffer, cr);
        }
        if (attrName.equals("LocalVariableTable")) {
            return new LocalVarAttr("LocalVariableTable", buffer);
        }
        if (attrName.equals("LocalVariableTypeTable")) {
            return new LocalVarAttr("LocalVariableTypeTable", buffer);
        }

        return new DefaultAttr(attrName, buffer);
    }

    private void processBytes(int len) {
        int sourcePos = sourceBuffer.position();
        int targetPos = sourceBuffer.position();

        for (int i = 0; i < len; i++) {
            assert sourceClass[sourcePos + i] == targetClass[targetPos + i];
        }

        skip(len);
    }

    private void skip(int n) {
        sourceBuffer.position(sourceBuffer.position() + n);
        targetBuffer.position(targetBuffer.position() + n);
    }

    private String processUtf() {
        processShort();
        return cr.readUTF8(sourceBuffer.position() - 2, charBuffer);
    }

    private int processShort() {
        int x1 = sourceBuffer.getShort() & 0xFFFF;
        int x2 = targetBuffer.getShort() & 0xFFFF;
        assert x1 == x2;

        return x1;
    }

    private int processInt() {
        int x1 = sourceBuffer.getInt();
        int x2 = targetBuffer.getInt();
        assert x1 == x2;

        return x1;
    }

    private void processInt(int expectedValue) {
        int x1 = sourceBuffer.getInt();
        int x2 = targetBuffer.getInt();
        assert x1 == x2;
        assert x1 == expectedValue;
    }

    public static void compare(byte[] sourceClass, byte[] targetClass) {
        ClassComparator comparator = new ClassComparator(sourceClass, targetClass);
        comparator.compare();
    }

    private static class Attr {
        protected final String name;

        private Attr(@NotNull String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

    private static class LocalVarAttr extends Attr {

        private final Set<ByteArrayString> elements = new LinkedHashSet<ByteArrayString>();

        private LocalVarAttr(@NotNull String name, ByteBuffer buffer) {
            super(name);

            int attrSize = buffer.getInt();
            int length = buffer.getShort();

            for (int i = 0; i < length; i++) {
                elements.add(new ByteArrayString(PackUtils.readBytes(buffer, 5 * 2)));
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            LocalVarAttr that = (LocalVarAttr) o;

            if (!elements.equals(that.elements)) return false;

            return true;
        }
    }

    private class ExceptionsAttr extends Attr {
        private final Set<String> exceptions = new LinkedHashSet<String>();

        public ExceptionsAttr(ByteBuffer buffer, ClassReader cr) {
            super("Exceptions");
            int size = buffer.getInt();
            int exceptionsCount = buffer.getShort() & 0xFFFF;

            assert size == 2 + exceptionsCount * 2;

            for (int i = 0; i < exceptionsCount; i++) {
                String exception = cr.readClass(buffer.position(), charBuffer);
                buffer.getShort();

                exceptions.add(exception);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ExceptionsAttr that = (ExceptionsAttr) o;

            if (!exceptions.equals(that.exceptions)) return false;

            return true;
        }
    }

    private static class DefaultAttr extends Attr {
        private byte[] data;

        public DefaultAttr(@NotNull String name, ByteBuffer buffer) {
            super(name);
            int size = buffer.getInt();
            data = PackUtils.readBytes(buffer, size);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            DefaultAttr that = (DefaultAttr) o;

            if (!name.equals(that.name)) return false;

            if (!Arrays.equals(data, that.data)) return false;

            return true;
        }
    }
}
