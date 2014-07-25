package com.ess.jloader.utils;

import com.google.common.base.Throwables;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ByteVector;
import org.objectweb.asm.ClassWriter;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class ClassWriterManager {

    private static Field poolField;
    private static Field indexField;

    private static Field dataField;
    private static Field lengthField;

    static {
        try {
            poolField = ClassWriter.class.getDeclaredField("pool");
            poolField.setAccessible(true);

            indexField = ClassWriter.class.getDeclaredField("index");
            indexField.setAccessible(true);

            dataField = ByteVector.class.getDeclaredField("data");
            dataField.setAccessible(true);

            lengthField = ByteVector.class.getDeclaredField("length");
            lengthField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw Throwables.propagate(e);
        }
    }

    private final ClassWriter classWriter;

    private final int constCount;

    private final ByteVector writerVector;

    private final List<ByteVector> vectors = new ArrayList<ByteVector>();

    private int head;
    private int reservedCount;

    private int savedIndex;

    public ClassWriterManager(@NotNull ClassWriter classWriter, int constCount) {
        this.classWriter = classWriter;
        this.constCount = constCount;

        head = constCount;

        try {
            writerVector = (ByteVector) poolField.get(classWriter);
        }
        catch (IllegalAccessException e) {
            throw Throwables.propagate(e);
        }
    }

    public ClassWriter getClassWriter() {
        return classWriter;
    }

    public void goHead(int n) {
        assert n > 0;

        if (savedIndex != 0) throw new IllegalStateException();

        try {
            poolField.set(classWriter, new ByteVector());

            savedIndex = (Integer)indexField.get(classWriter);

            if (savedIndex + n > head) throw new RuntimeException();

            reservedCount = n;

            head -= n;

            indexField.set(classWriter, head);

        } catch (IllegalAccessException e) {
            throw Throwables.propagate(e);
        }
    }

    public void goBack() {
        if (savedIndex == 0) throw new IllegalStateException();

        try {
            int index = (Integer) indexField.get(classWriter);

            if (index != head + reservedCount) {
                throw new RuntimeException();
            }

            ByteVector pool = (ByteVector) poolField.get(classWriter);
            vectors.add(pool);

            poolField.set(classWriter, writerVector);

            indexField.set(classWriter, savedIndex);
            savedIndex = 0;
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public void finish() {
        if (savedIndex > 0) {
            goBack();
        }

        try {
            int index = (Integer) indexField.get(classWriter);

            if (index != head) {
                throw new RuntimeException();
            }

            ByteVector pool = (ByteVector) poolField.get(classWriter);

            for (int i = vectors.size(); --i >= 0; ) {
                ByteVector vector = vectors.get(i);

                byte[] data = (byte[]) dataField.get(vector);
                int length = (Integer)lengthField.get(vector);

                pool.putByteArray(data, 0, length);
            }

            indexField.set(classWriter, constCount);

            poolField.set(classWriter, writerVector);

            savedIndex = 0;
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }

    }
}
