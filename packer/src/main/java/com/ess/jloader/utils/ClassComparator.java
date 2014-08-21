package com.ess.jloader.utils;

import com.ess.jloader.packer.PackUtils;
import org.objectweb.asm.ClassReader;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
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
        assertArrayEquals(sourceClassReader.header);

        cr = sourceClassReader;
        charBuffer = new char[cr.getMaxStringLength()];

        processShort(); // access flags
        processShort(); // this class index
        processShort(); // super class index

        int interfaceCount = processShort();
        assertArrayEquals(interfaceCount * 2);

        int fieldCount = processShort();
        for (int i = 0; i < fieldCount; i++) {
            processShort(); // access flags

            String name = processUtf();
            String descr = processUtf();

            processAttributes();
        }

        int methodCount = processShort();
        for (int i = 0; i < methodCount; i++) {
            processShort(); // access flags

            String name = processUtf();
            String descr = processUtf();

            processAttributes();
        }

        processAttributes();
    }

    private void processAttributes() {
        int attrCount = processShort();

        Set<Attr> sourceAttr = new LinkedHashSet<Attr>();
        Set<Attr> targetAttr = new LinkedHashSet<Attr>();

        for (int i = 0; i < attrCount; i++) {
            sourceAttr.add(readAttr(sourceBuffer, sourceClassReader));
            targetAttr.add(readAttr(targetBuffer, targetClassReader));
        }

        for (Attr attr : sourceAttr) {
            assert targetAttr.contains(attr);
        }
    }

    private Attr readAttr(ByteBuffer buffer, ClassReader cr) {
        String attrName = cr.readUTF8(buffer.position(), charBuffer);
        buffer.getShort();

        if (attrName.equals("Exceptions")) {
            return new ExceptionsAttr(buffer, cr);
        }

        if (attrName.equals("Code")) {
            return new CodeAttr(buffer);
        }

        return new DefaultAttr(buffer);
    }

    private void assertArrayEquals(int len) {
        for (int i = sourceBuffer.position(), end = sourceBuffer.position() + len; i < end; i++) {
            assert sourceClass[i] == targetClass[i];
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

    private interface Attr {

    }

    private class CodeAttr implements Attr {
        private byte[] data;

        public CodeAttr(ByteBuffer buffer) {
            int size = buffer.getInt();
            data = PackUtils.readBytes(buffer, size);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CodeAttr that = (CodeAttr) o;

//            if (data.length != that.data.length)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return data.length;
        }
    }

    private class ExceptionsAttr implements Attr {
        private final Set<String> exceptions = new LinkedHashSet<String>();

        public ExceptionsAttr(ByteBuffer buffer, ClassReader cr) {
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

        @Override
        public int hashCode() {
            return exceptions.hashCode();
        }
    }

    private static class DefaultAttr implements Attr {
        private byte[] data;

        public DefaultAttr(ByteBuffer buffer) {
            int size = buffer.getInt();
            data = PackUtils.readBytes(buffer, size);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            DefaultAttr that = (DefaultAttr) o;

            if (!Arrays.equals(data, that.data)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(data);
        }
    }
}
